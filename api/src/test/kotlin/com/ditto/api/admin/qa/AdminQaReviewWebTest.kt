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
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.entity.RematchStatus
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.ReviewProgressStatus
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.containsString
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
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
    val groupMatchId = 7L

    fun saveMember(nickname: String): Member =
        memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = MemberStatus.ACTIVE),
        )

    fun saveEndedRoom(room: ChatRoom, members: List<Member>): ChatRoom {
        val saved = chatRoomRepository.save(room.apply { expire(LocalDateTime.now()) })
        chatRoomMemberRepository.saveAll(members.map { ChatRoomMemberFixture.create(saved.id, it.id) })
        return saved
    }

    fun saveEndedGroupRoom(vararg members: Member): ChatRoom =
        saveEndedRoom(ChatRoomFixture.group(sourceId = groupMatchId), members.toList())

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

    fun saveGroupRematch(room: ChatRoom, memberA: Member, memberB: Member, wantedBy: Member? = null): Rematch {
        val pair = RematchFixture.create(
            sourceGroupMatchId = groupMatchId,
            sourceChatRoomId = room.id,
            memberIdA = memberA.id,
            memberIdB = memberB.id,
        )
        wantedBy?.let { pair.submitWants(it.id, true, LocalDateTime.now()) }
        return rematchRepository.save(pair)
    }

    fun rematchStatusOf(pair: Rematch): RematchStatus = rematchRepository.findByIdOrNull(pair.id).shouldNotBeNull().status

    fun reviewStatusOf(review: MemberReview): ReviewProgressStatus =
        memberReviewRepository.findByIdOrNull(review.id).shouldNotBeNull().status

    fun submitAsDummy(dummy: Member, room: ChatRoom, review: MemberReview, target: Member, wantsRematch: Boolean? = null) =
        mockMvc.perform(
            post(
                "/admin/qa/dummies/{dummyId}/rooms/{roomId}/reviews/{reviewId}/targets/{targetId}",
                dummy.id, room.id, review.id, target.id,
            )
                .param("meetingStatus", "MET")
                .param("rating", "4")
                .param("comment", "  즐거웠어요  ")
                .apply { wantsRematch?.let { param("wantsOneToOneRematch", it.toString()) } }
                .with(authentication(admin)).with(csrf()),
        )

    fun submitPendingForAllDummies(room: ChatRoom, rematchChoice: QaBulkRematchChoice? = null) =
        mockMvc.perform(
            post("/admin/qa/rooms/{roomId}/reviews/submit-all-dummies", room.id)
                .apply { rematchChoice?.let { param("rematchChoice", it.name) } }
                .with(authentication(admin)).with(csrf()),
        )

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
            val room = saveEndedGroupRoom(tester, dummy, otherDummy)
            openReview(room, tester, listOf(dummy, otherDummy))
            openReview(room, dummy, listOf(tester, otherDummy))
            val pair = saveGroupRematch(room, tester, dummy, wantedBy = tester)

            val review = reviewsOf(room).single()

            review.author.id shouldBe dummy.id
            review.canRematch shouldBe true
            review.answeredCount shouldBe 0
            val towardTester = review.targets.first { it.member.id == tester.id }
            towardTester.isDummy shouldBe false
            towardTester.rematch.shouldNotBeNull().let {
                it.rematchId shouldBe pair.id
                it.status shouldBe RematchStatus.WAITING
                it.authorWants shouldBe null
                it.counterpartWants shouldBe true
            }
            review.targets.first { it.member.id == otherDummy.id }.rematch.shouldBeNull()
        }

        "탈퇴로 취소된 쌍은 상대가 낸 의사를 감춘다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveEndedGroupRoom(tester, dummy, saveMember("dummy-male-bbbb"))
            openReview(room, dummy, listOf(tester))
            val pair = saveGroupRematch(room, tester, dummy, wantedBy = tester)
            rematchRepository.save(pair.apply { cancelForMemberLeave() })

            reviewsOf(room).single().targets.single().rematch.shouldNotBeNull().let {
                it.isCancelledByMemberLeave shouldBe true
                it.counterpartWants shouldBe null
            }
        }

        "성사된 쌍은 예약된 재매칭 방을 함께 보여 준다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveEndedGroupRoom(tester, dummy, saveMember("dummy-male-bbbb"))
            val review = openReview(room, dummy, listOf(tester))
            val pair = saveGroupRematch(room, tester, dummy, wantedBy = tester)
            submitAsDummy(dummy, room, review, tester, wantsRematch = true)
            val rematchRoom = chatRoomRepository.save(ChatRoomFixture.rematch(sourceId = pair.id))

            reviewsOf(room).single().targets.single().rematch.shouldNotBeNull().room.shouldNotBeNull().roomId shouldBe rematchRoom.id
        }

        "1:1 방은 재매칭을 받지 않는다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveEndedRoom(ChatRoomFixture.personal(), listOf(tester, dummy))
            openReview(room, dummy, listOf(tester))

            reviewsOf(room).single().canRematch shouldBe false
        }

        "평가가 열리지 않은 방은 평가가 비어 있다" {
            val room = saveEndedRoom(ChatRoomFixture.personal(), listOf(saveMember("테스터"), saveMember("dummy-female-aaaa")))

            reviewsOf(room) shouldBe emptyList()
        }
    }

    "대상별 제출" - {
        "상대가 이미 원했으면 더미가 원한다고 내는 순간 성사되고 결과를 알린다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveEndedGroupRoom(tester, dummy, saveMember("dummy-male-bbbb"))
            val review = openReview(room, dummy, listOf(tester))
            val pair = saveGroupRematch(room, tester, dummy, wantedBy = tester)

            submitAsDummy(dummy, room, review, tester, wantsRematch = true)
                .andExpect(redirectedUrl("/admin/qa/rooms/${room.id}#qa-reviews"))
                .andExpect(flash().attribute("message", "dummy-female-aaaa(#${dummy.id}) · 테스터(#${tester.id}) 평가 내기 완료 · 재매칭 성사"))

            rematchStatusOf(pair) shouldBe RematchStatus.MATCHED
            reviewsOf(room).single().targets.single().let {
                it.isAnswered shouldBe true
                it.answerSummary shouldBe "만났어요 · 4점 · 즐거웠어요"
                it.rematch.shouldNotBeNull().authorWants shouldBe true
            }
        }

        "1:1 평가는 재매칭 없이 내고 마지막 대상이면 평가가 끝난다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveEndedRoom(ChatRoomFixture.personal(), listOf(tester, dummy))
            val review = openReview(room, dummy, listOf(tester))

            submitAsDummy(dummy, room, review, tester)
                .andExpect(flash().attribute("message", "dummy-female-aaaa(#${dummy.id}) · 테스터(#${tester.id}) 평가 내기 완료"))

            reviewStatusOf(review) shouldBe ReviewProgressStatus.COMPLETED
        }

        "그룹 평가를 재매칭 의사 없이 내면 앱의 거부 코드를 띄운다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveEndedGroupRoom(tester, dummy, saveMember("dummy-male-bbbb"))
            val review = openReview(room, dummy, listOf(tester))
            saveGroupRematch(room, tester, dummy)

            submitAsDummy(dummy, room, review, tester, wantsRematch = null)
                .andExpect(flash().attribute("error", containsString("테스터(#${tester.id}) 평가 내기 실패")))

            reviewsOf(room).single().targets.single().isAnswered shouldBe false
        }
    }

    "일괄 제출" - {
        "기본은 실회원에게만 원해 테스터와는 성사되고 더미끼리는 성사되지 않는다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val otherDummy = saveMember("dummy-male-bbbb")
            val room = saveEndedGroupRoom(tester, dummy, otherDummy)
            val dummyReview = openReview(room, dummy, listOf(tester, otherDummy))
            val otherDummyReview = openReview(room, otherDummy, listOf(tester, dummy))
            val testerAndDummy = saveGroupRematch(room, tester, dummy, wantedBy = tester)
            val testerAndOtherDummy = saveGroupRematch(room, tester, otherDummy)
            val dummyAndOtherDummy = saveGroupRematch(room, dummy, otherDummy)

            submitPendingForAllDummies(room)
                .andExpect(redirectedUrl("/admin/qa/rooms/${room.id}#qa-reviews"))
                .andExpect(flash().attribute("message", "방 #${room.id} 더미 남은 평가 모두 내기 (2명) 완료"))

            reviewStatusOf(dummyReview) shouldBe ReviewProgressStatus.COMPLETED
            reviewStatusOf(otherDummyReview) shouldBe ReviewProgressStatus.COMPLETED
            rematchStatusOf(testerAndDummy) shouldBe RematchStatus.MATCHED
            rematchStatusOf(testerAndOtherDummy) shouldBe RematchStatus.WAITING
            rematchStatusOf(dummyAndOtherDummy) shouldBe RematchStatus.CANCELLED
        }

        "모두에게 원하면 더미끼리도 성사된다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val otherDummy = saveMember("dummy-male-bbbb")
            val room = saveEndedGroupRoom(tester, dummy, otherDummy)
            openReview(room, dummy, listOf(otherDummy))
            openReview(room, otherDummy, listOf(dummy))
            val dummyAndOtherDummy = saveGroupRematch(room, dummy, otherDummy)

            submitPendingForAllDummies(room, QaBulkRematchChoice.EVERYONE).andExpect(flash().attributeExists("message"))

            rematchStatusOf(dummyAndOtherDummy) shouldBe RematchStatus.MATCHED
        }

        "모두 안 함으로 내면 테스터가 원한 쌍도 불성사가 된다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveEndedGroupRoom(tester, dummy, saveMember("dummy-male-bbbb"))
            openReview(room, dummy, listOf(tester))
            val testerAndDummy = saveGroupRematch(room, tester, dummy, wantedBy = tester)

            submitPendingForAllDummies(room, QaBulkRematchChoice.NOBODY).andExpect(flash().attributeExists("message"))

            rematchStatusOf(testerAndDummy) shouldBe RematchStatus.CANCELLED
        }

        "중간 대상에서 거부되면 몇 명까지 냈는지 알린다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val otherDummy = saveMember("dummy-male-bbbb")
            val room = saveEndedGroupRoom(tester, dummy, otherDummy)
            val review = openReview(room, dummy, listOf(tester, otherDummy))
            saveGroupRematch(room, tester, dummy)

            submitPendingForAllDummies(room)
                .andExpect(
                    flash().attribute(
                        "error",
                        containsString("dummy-female-aaaa(#${dummy.id}) 2명 중 1명 낸 뒤 dummy-male-bbbb(#${otherDummy.id})에서 멈춤"),
                    ),
                )

            reviewStatusOf(review) shouldBe ReviewProgressStatus.IN_PROGRESS
        }

        "남은 평가가 없으면 대상 더미가 없다고 알린다" {
            val room = saveEndedRoom(ChatRoomFixture.personal(), listOf(saveMember("테스터"), saveMember("dummy-female-aaaa")))

            submitPendingForAllDummies(room)
                .andExpect(flash().attribute("error", "방 #${room.id} 더미 남은 평가 모두 내기: 대상 더미가 없습니다."))
        }
    }
})
