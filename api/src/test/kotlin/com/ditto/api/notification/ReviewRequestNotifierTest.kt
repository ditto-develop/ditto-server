package com.ditto.api.notification

import com.ditto.api.notification.notifier.ReviewRequestNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class ReviewRequestNotifierTest(
    private val reviewRequestNotifier: ReviewRequestNotifier,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMember(nickname: String) = memberRepository.save(
        MemberFixture.create(nickname = nickname, email = "$nickname@example.com", status = MemberStatus.ACTIVE),
    )

    "끝난 방의 참여자에게 평가 요청을 알린다" - {
        "1:1 방은 상대 닉네임이 문구에 들어간다" {
            val me = saveMember("나")
            val counterpart = saveMember("댕이누나")
            val room = chatRoomRepository.save(ChatRoomFixture.personal())
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = me.id))
            chatRoomMemberRepository.save(
                ChatRoomMemberFixture.create(roomId = room.id, memberId = counterpart.id),
            )

            reviewRequestNotifier.notifyFor(listOf(room.id)) shouldBe 2

            val mine = notificationRepository.findAll().single { it.memberId == me.id }
            mine.type shouldBe NotificationType.REVIEW_REQUEST
            mine.title shouldBe "이번 만남은 어떠셨나요?"
            mine.body shouldBe "댕이누나님과의 만남을 평가해주세요."
            mine.targetId shouldBe room.id
        }

        "그룹 방은 이름 대신 인원으로 말한다 — 상대가 여럿이라 이름을 하나만 쓸 수 없다" {
            val me = saveMember("나")
            val others = (1..3).map { saveMember("멤버$it") }
            val room = chatRoomRepository.save(ChatRoomFixture.group())
            (listOf(me) + others).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = it.id))
            }

            reviewRequestNotifier.notifyFor(listOf(room.id)) shouldBe 4

            notificationRepository.findAll().single { it.memberId == me.id }.body shouldBe
                "함께한 3명과의 만남을 평가해주세요."
        }

        // 재매칭 채팅이 끝나면 평가를 열지 않는다(#132 결정) — 알리면 평가할 것이 없는 화면으로 보낸다.
        "재매칭 방에는 알리지 않는다" {
            val me = saveMember("나")
            val counterpart = saveMember("상대")
            val room = chatRoomRepository.save(ChatRoomFixture.rematch())
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = me.id))
            chatRoomMemberRepository.save(
                ChatRoomMemberFixture.create(roomId = room.id, memberId = counterpart.id),
            )

            reviewRequestNotifier.notifyFor(listOf(room.id)) shouldBe 0

            notificationRepository.count() shouldBe 0
        }

        "같은 방을 다시 넘겨도 중복으로 남지 않는다 — 사용자 종료와 만료 마감이 겹칠 수 있다" {
            val me = saveMember("나")
            val counterpart = saveMember("상대")
            val room = chatRoomRepository.save(ChatRoomFixture.personal())
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = me.id))
            chatRoomMemberRepository.save(
                ChatRoomMemberFixture.create(roomId = room.id, memberId = counterpart.id),
            )
            reviewRequestNotifier.notifyFor(listOf(room.id))

            reviewRequestNotifier.notifyFor(listOf(room.id)) shouldBe 0

            notificationRepository.count() shouldBe 2
        }

        "끝난 방이 없으면 아무 것도 하지 않는다" {
            reviewRequestNotifier.notifyFor(emptyList()) shouldBe 0
        }
    }
})
