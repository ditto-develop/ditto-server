package com.ditto.api.admin.qa

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.qa.dto.QaRoomSummary
import com.ditto.api.admin.qa.dto.QaRoomView
import com.ditto.api.admin.qa.dto.QaVote
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.entity.ChatEndReason
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import io.kotest.matchers.collections.shouldHaveSize
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import javax.sql.DataSource

@AutoConfigureMockMvc
class AdminQaRoomWebTest(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    fun MockHttpServletRequestBuilder.asAdmin() = with(authentication(admin)).with(csrf())

    fun saveMember(nickname: String): Member =
        memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = MemberStatus.ACTIVE),
        )

    fun saveRoom(room: ChatRoom, members: List<Member>): ChatRoom {
        val saved = chatRoomRepository.save(room)
        chatRoomMemberRepository.saveAll(members.map { ChatRoomMemberFixture.create(saved.id, it.id) })
        return saved
    }

    fun saveMessage(room: ChatRoom, sender: Member, content: String = "안녕하세요"): ChatMessage =
        chatMessageRepository.save(ChatMessageFixture.create(roomId = room.id, senderId = sender.id, content = content))

    fun messagesIn(room: ChatRoom): List<ChatMessage> =
        chatMessageRepository.findByRoomIdWithCursor(room.id, cursor = null, size = 100).reversed()

    /** 실회원 한 명과 [actor], 다른 더미 한 명이 있는 그룹 방. */
    fun saveGroupRoomWith(actor: Member): ChatRoom =
        saveRoom(ChatRoomFixture.group(), listOf(saveMember("테스터"), actor, saveMember("dummy-female-bbbb")))

    fun roomView(room: ChatRoom): QaRoomView =
        mockMvc.perform(get("/admin/qa/rooms/{id}", room.id).with(authentication(admin)))
            .andExpect(status().isOk)
            .andReturn().modelAndView.shouldNotBeNull().model["room"] as QaRoomView

    fun createSampleVote(room: ChatRoom, creator: Member): QaVote {
        mockMvc.perform(post("/admin/qa/rooms/{id}/votes", room.id).param("dummyId", creator.id.toString()).asAdmin())
            .andExpect(flash().attributeExists("message"))
        return roomView(room).openVote.shouldNotBeNull()
    }

    fun lastReadMessageIdOf(room: ChatRoom, member: Member): Long? =
        chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, member.id)?.lastReadMessageId

    "화면" - {
        "콘솔은 더미가 있는 방을 진행 중인 방부터 보여준다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val activeRoom = saveRoom(ChatRoomFixture.personal(sourceId = 1L), listOf(tester, dummy))
            val endedRoom = saveRoom(ChatRoomFixture.personal(sourceId = 2L), listOf(tester, dummy))
                .also { chatRoomRepository.save(it.apply { expire(LocalDateTime.now()) }) }
            saveRoom(ChatRoomFixture.personal(sourceId = 3L), listOf(tester, saveMember("실회원")))

            @Suppress("UNCHECKED_CAST")
            val rooms = mockMvc.perform(get("/admin/qa").with(authentication(admin)))
                .andExpect(status().isOk)
                .andReturn().modelAndView.shouldNotBeNull().model["rooms"] as List<QaRoomSummary>

            rooms.map { it.roomId } shouldBe listOf(activeRoom.id, endedRoom.id)
            rooms.first().memberCount shouldBe 2
            rooms.first().dummyCount shouldBe 1
        }

        "방 화면은 최근 메시지와 참여자의 읽음 커서를 보여준다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveRoom(ChatRoomFixture.personal(), listOf(tester, dummy))
            val message = saveMessage(room, tester)

            val view = mockMvc.perform(get("/admin/qa/rooms/{id}", room.id).with(authentication(admin)))
                .andExpect(status().isOk)
                .andReturn().modelAndView.shouldNotBeNull().model["room"] as QaRoomView

            view.messages.single().let {
                it.messageId shouldBe message.id
                it.fromDummy shouldBe false
                it.unreadCount shouldBe 1
            }
            view.members.map { it.member.id } shouldBe listOf(tester.id, dummy.id)
            view.activeDummies.map { it.id } shouldBe listOf(dummy.id)
        }

        "없는 방은 콘솔로 돌려보낸다" {
            mockMvc.perform(get("/admin/qa/rooms/{id}", 999L).with(authentication(admin)))
                .andExpect(redirectedUrl("/admin/qa#rooms"))
                .andExpect(flash().attributeExists("error"))
        }
    }

    "더미로 메시지 보내기" - {
        "입력한 내용을 더미가 보낸다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveRoom(ChatRoomFixture.personal(), listOf(tester, dummy))

            mockMvc.perform(
                post("/admin/qa/rooms/{id}/messages", room.id)
                    .param("dummyId", dummy.id.toString())
                    .param("content", "  반가워요  ")
                    .asAdmin(),
            )
                .andExpect(redirectedUrl("/admin/qa/rooms/${room.id}"))
                .andExpect(flash().attributeExists("message"))

            messagesIn(room).single().let {
                it.senderId shouldBe dummy.id
                it.content shouldBe "반가워요"
            }
        }

        "연속 입력은 35개를 차례로 보낸다" {
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveRoom(ChatRoomFixture.personal(), listOf(saveMember("테스터"), dummy))

            mockMvc.perform(
                post("/admin/qa/rooms/{id}/messages", room.id)
                    .param("dummyId", dummy.id.toString())
                    .param("preset", "BURST")
                    .asAdmin(),
            ).andExpect(flash().attributeExists("message"))

            val messages = messagesIn(room)
            messages shouldHaveSize 35
            messages.first().content shouldBe "연속 메시지 1/35"
            messages.last().content shouldBe "연속 메시지 35/35"
        }

        "1000자 입력은 상한 길이 그대로 보낸다" {
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveRoom(ChatRoomFixture.personal(), listOf(saveMember("테스터"), dummy))

            mockMvc.perform(
                post("/admin/qa/rooms/{id}/messages", room.id)
                    .param("dummyId", dummy.id.toString())
                    .param("preset", "MAX_LENGTH")
                    .asAdmin(),
            ).andExpect(flash().attributeExists("message"))

            messagesIn(room).single().content.length shouldBe 1000
        }

        "개방 전 방에서는 앱과 같은 거부를 보여준다" {
            val dummy = saveMember("dummy-female-aaaa")
            val wednesday = LocalDateTime.of(2026, 3, 11, 12, 0)
            val room = saveRoom(ChatRoomFixture.personal(now = wednesday), listOf(saveMember("테스터"), dummy))

            mockMvc.perform(
                post("/admin/qa/rooms/{id}/messages", room.id)
                    .param("dummyId", dummy.id.toString())
                    .param("content", "안녕하세요")
                    .asAdmin(),
            ).andExpect(flash().attribute("error", containsString("(7005)")))

            messagesIn(room) shouldHaveSize 0
        }

        "실회원으로는 보내지 않는다" {
            val tester = saveMember("테스터")
            val room = saveRoom(ChatRoomFixture.personal(), listOf(tester, saveMember("dummy-female-aaaa")))

            mockMvc.perform(
                post("/admin/qa/rooms/{id}/messages", room.id)
                    .param("dummyId", tester.id.toString())
                    .param("content", "안녕하세요")
                    .asAdmin(),
            ).andExpect(flash().attribute("error", containsString("더미 회원만")))

            messagesIn(room) shouldHaveSize 0
        }
    }

    "더미 읽음" - {
        "더미가 최신 메시지까지 읽는다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveRoom(ChatRoomFixture.personal(), listOf(tester, dummy))
            saveMessage(room, tester, "첫 메시지")
            val latest = saveMessage(room, tester, "두 번째 메시지")

            mockMvc.perform(post("/admin/qa/dummies/{dummyId}/rooms/{roomId}/read", dummy.id, room.id).asAdmin())
                .andExpect(flash().attributeExists("message"))

            lastReadMessageIdOf(room, dummy) shouldBe latest.id
        }

        "더미 모두 읽음은 나가지 않은 더미 전원의 커서를 옮긴다" {
            val tester = saveMember("테스터")
            val dummies = listOf("dummy-male-aaaa", "dummy-female-bbbb").map { saveMember(it) }
            val room = saveRoom(ChatRoomFixture.group(), dummies + tester)
            val latest = saveMessage(room, tester)

            mockMvc.perform(post("/admin/qa/rooms/{id}/read-all-dummies", room.id).asAdmin())
                .andExpect(flash().attributeExists("message"))

            dummies.map { lastReadMessageIdOf(room, it) } shouldBe listOf(latest.id, latest.id)
            lastReadMessageIdOf(room, tester) shouldBe null
        }

        "읽을 메시지가 없으면 알려준다" {
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveRoom(ChatRoomFixture.personal(), listOf(saveMember("테스터"), dummy))

            mockMvc.perform(post("/admin/qa/dummies/{dummyId}/rooms/{roomId}/read", dummy.id, room.id).asAdmin())
                .andExpect(flash().attribute("error", containsString("읽을 메시지가 없습니다")))
        }
    }

    "나가기·종료" - {
        val dummyRoomUrl = "/admin/qa/dummies/{dummyId}/rooms/{roomId}"

        "그룹 방에서 더미가 나가면 방은 이어지고 MEMBER_LEFT가 남는다" {
            val tester = saveMember("테스터")
            val leaving = saveMember("dummy-male-aaaa")
            val room = saveRoom(ChatRoomFixture.group(), listOf(tester, leaving, saveMember("dummy-female-bbbb")))
            mockMvc.perform(get("/admin/qa/rooms/{id}", room.id).with(authentication(admin)))
                .andExpect(status().isOk)

            mockMvc.perform(post("$dummyRoomUrl/leave", leaving.id, room.id).asAdmin())
                .andExpect(flash().attributeExists("message"))

            chatRoomRepository.findByIdOrNull(room.id)?.isEnded shouldBe false
            chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, leaving.id)?.hasLeft shouldBe true
            messagesIn(room).last().let {
                it.messageType shouldBe ChatMessageType.SYSTEM
                it.content shouldBe "MEMBER_LEFT"
                it.senderId shouldBe leaving.id
            }
        }

        "그룹 방에 한 명만 남으면 방이 해체된다" {
            val leaving = saveMember("dummy-male-aaaa")
            val room = saveRoom(ChatRoomFixture.group(), listOf(saveMember("테스터"), leaving))

            mockMvc.perform(post("$dummyRoomUrl/leave", leaving.id, room.id).asAdmin())
                .andExpect(flash().attributeExists("message"))

            chatRoomRepository.findByIdOrNull(room.id)?.endReason shouldBe ChatEndReason.INSUFFICIENT_MEMBERS
            messagesIn(room).last().content shouldBe "INSUFFICIENT_MEMBERS"
        }

        "1:1 방에서 더미가 종료하면 방이 끝나고 USER_LEFT가 남는다" {
            val dummy = saveMember("dummy-female-aaaa")
            val room = saveRoom(ChatRoomFixture.personal(), listOf(saveMember("테스터"), dummy))

            mockMvc.perform(post("$dummyRoomUrl/end", dummy.id, room.id).asAdmin())
                .andExpect(flash().attributeExists("message"))

            chatRoomRepository.findByIdOrNull(room.id)?.endReason shouldBe ChatEndReason.USER_ENDED
            messagesIn(room).last().content shouldBe "USER_LEFT"
        }
    }

    "투표" - {
        "더미가 샘플 투표를 만들면 열린 투표와 VOTE_CREATED가 남는다" {
            val creator = saveMember("dummy-male-aaaa")
            val room = saveGroupRoomWith(creator)

            val vote = createSampleVote(room, creator)

            vote.placeOptions shouldHaveSize 3
            vote.timeOptions shouldHaveSize 2
            messagesIn(room).last().let {
                it.content shouldBe "VOTE_CREATED:${vote.voteId}"
                it.senderId shouldBe creator.id
            }
        }

        "더미가 고른 선택지로 투표한다" {
            val voter = saveMember("dummy-male-aaaa")
            val room = saveGroupRoomWith(voter)
            val vote = createSampleVote(room, voter)
            val place = vote.placeOptions[1]
            val time = vote.timeOptions[0]

            mockMvc.perform(
                post("/admin/qa/rooms/{roomId}/votes/{voteId}/cast", room.id, vote.voteId)
                    .param("dummyId", voter.id.toString())
                    .param("placeIds", place.optionId.toString())
                    .param("timeIds", time.optionId.toString())
                    .asAdmin(),
            ).andExpect(flash().attributeExists("message"))

            val updated = roomView(room).openVote.shouldNotBeNull()
            updated.votedCount shouldBe 1
            updated.placeOptions[1].voters.map { it.id } shouldBe listOf(voter.id)
            updated.timeOptions[0].voters.map { it.id } shouldBe listOf(voter.id)
        }

        "더미 모두 무작위 투표하면 나가지 않은 더미가 모두 투표한다" {
            val dummies = listOf("dummy-male-aaaa", "dummy-female-bbbb").map { saveMember(it) }
            val room = saveRoom(ChatRoomFixture.group(), dummies + saveMember("테스터"))
            val vote = createSampleVote(room, dummies.first())

            mockMvc.perform(
                post("/admin/qa/rooms/{roomId}/votes/{voteId}/cast-random-all-dummies", room.id, vote.voteId).asAdmin(),
            ).andExpect(flash().attributeExists("message"))

            val updated = roomView(room).openVote.shouldNotBeNull()
            updated.votedCount shouldBe 2
            updated.placeOptions.sumOf { it.voters.size } shouldBe 2
        }

        "더미가 투표를 마감하면 VOTE_CLOSED가 남는다" {
            val closer = saveMember("dummy-male-aaaa")
            val room = saveGroupRoomWith(closer)
            val vote = createSampleVote(room, closer)

            mockMvc.perform(
                post("/admin/qa/rooms/{roomId}/votes/{voteId}/close", room.id, vote.voteId)
                    .param("dummyId", closer.id.toString())
                    .asAdmin(),
            ).andExpect(flash().attributeExists("message"))

            val view = roomView(room)
            view.openVote shouldBe null
            view.closedVotes.single().voteId shouldBe vote.voteId
            messagesIn(room).last().content shouldBe "VOTE_CLOSED:${vote.voteId}"
        }

        "1:1 방에서는 앱과 같은 거부를 보여준다" {
            val dummy = saveMember("dummy-male-aaaa")
            val room = saveRoom(ChatRoomFixture.personal(), listOf(saveMember("테스터"), dummy))

            mockMvc.perform(post("/admin/qa/rooms/{id}/votes", room.id).param("dummyId", dummy.id.toString()).asAdmin())
                .andExpect(flash().attribute("error", containsString("(8208)")))
        }
    }
})
