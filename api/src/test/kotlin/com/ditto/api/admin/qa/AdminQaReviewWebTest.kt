package com.ditto.api.admin.qa

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.qa.dto.QaReview
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.entity.RematchStatus
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import javax.sql.DataSource

@AutoConfigureMockMvc
class AdminQaReviewWebTest(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val rematchRepository: RematchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    fun saveMember(nickname: String): Member =
        memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = MemberStatus.ACTIVE),
        )

    fun saveEndedRoom(room: ChatRoom, members: List<Member>): ChatRoom {
        val saved = chatRoomRepository.save(room.apply { expire(LocalDateTime.now()) })
        chatRoomMemberRepository.saveAll(members.map { ChatRoomMemberFixture.create(saved.id, it.id) })
        return saved
    }

    /** 끝난 방에서 [author]에게 열린 평가. 대상은 방의 나머지 사람들이다. */
    fun openReview(room: ChatRoom, author: Member, targets: List<Member>): MemberReview {
        val review = memberReviewRepository.save(
            MemberReviewFixture.create(
                authorMemberId = author.id,
                matchType = room.sourceType,
                matchId = room.sourceId,
                chatRoomId = room.id,
            ),
        )
        reviewAnswerRepository.saveAll(targets.map { ReviewAnswerFixture.pending(memberReviewId = review.id, reviewedMemberId = it.id) })
        return review
    }

    fun reviewsOf(room: ChatRoom): List<QaReview> {
        @Suppress("UNCHECKED_CAST")
        return mockMvc.perform(get("/admin/qa/rooms/{id}", room.id).with(authentication(admin)))
            .andExpect(status().isOk)
            .andReturn().modelAndView.shouldNotBeNull().model["reviews"] as List<QaReview>
    }

    "화면" - {
        "그룹 방은 더미 평가마다 대상과 양쪽 재매칭 의사를 보여 준다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val otherDummy = saveMember("dummy-male-bbbb")
            val room = saveEndedRoom(ChatRoomFixture.group(sourceId = 7L), listOf(tester, dummy, otherDummy))
            openReview(room, tester, listOf(dummy, otherDummy))
            openReview(room, dummy, listOf(tester, otherDummy))
            rematchRepository.save(
                RematchFixture.create(sourceGroupMatchId = 7L, sourceChatRoomId = room.id, memberIdA = tester.id, memberIdB = dummy.id)
                    .apply { submitWants(tester.id, true, LocalDateTime.now()) },
            )

            val review = reviewsOf(room).single()

            review.author.id shouldBe dummy.id
            review.canRematch shouldBe true
            review.pendingTargets.size shouldBe 2
            val towardTester = review.targets.first { it.member.id == tester.id }
            towardTester.authorWantsRematch shouldBe null
            towardTester.counterpartWantsRematch shouldBe true
            towardTester.rematchStatus shouldBe RematchStatus.WAITING
            review.targets.first { it.member.id == otherDummy.id }.rematchStatus shouldBe null
        }

        "1:1 방은 재매칭 칸 없이 그리고, 평가가 열리면 새로고침을 안내한다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveEndedRoom(ChatRoomFixture.personal(), listOf(tester, dummy))
            openReview(room, dummy, listOf(tester))

            reviewsOf(room).single().canRematch shouldBe false
            mockMvc.perform(get("/admin/qa/rooms/{id}", room.id).with(authentication(admin)))
                .andExpect(content().string(containsString("더미 평가가 열렸습니다.")))
                .andExpect(content().string(not(containsString("title=\"더미의 의사 · 상대의 의사 · 쌍 상태\""))))
        }

        "평가가 열리지 않은 방은 평가 칸을 그리지 않는다" {
            val room = saveEndedRoom(ChatRoomFixture.personal(), listOf(saveMember("테스터"), saveMember("dummy-female-aaaa")))

            mockMvc.perform(get("/admin/qa/rooms/{id}", room.id).with(authentication(admin)))
                .andExpect(content().string(not(containsString("id=\"qa-reviews\""))))
                .andExpect(content().string(not(containsString("더미 평가가 열렸습니다."))))
        }
    }
})
