package com.ditto.api.notification

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.notification.notifier.ChatMessageNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private const val ROOM = 1L

class ChatMessageNotifierTest(
    private val chatMessageNotifier: ChatMessageNotifier,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMember(nickname: String) = memberRepository.save(
        MemberFixture.create(nickname = nickname, email = "$nickname@example.com", status = MemberStatus.ACTIVE),
    )

    fun message(
        senderId: Long,
        content: String = "주말에 시간 괜찮으세요?",
        messageType: ChatMessageType = ChatMessageType.TEXT,
    ) = ChatMessageResponse(
        id = 1L,
        roomId = ROOM,
        senderId = senderId,
        messageType = messageType,
        content = content,
        imageUrl = null,
        createdAt = LocalDateTime.now(),
    )

    "새 메시지를 상대에게 알린다" - {
        "보낸 사람에게는 남기지 않는다" {
            val sender = saveMember("산책러버")
            val receiver = saveMember("받는사람")
            listOf(sender, receiver).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = it.id))
            }

            chatMessageNotifier.notifyNewMessage(message(sender.id)) shouldBe 1

            val notification = notificationRepository.findAll().single()
            notification.memberId shouldBe receiver.id
            notification.type shouldBe NotificationType.CHAT_MESSAGE
            notification.title shouldBe "산책러버님의 새 메시지"
            notification.body shouldBe "주말에 시간 괜찮으세요?"
            notification.targetId shouldBe ROOM
        }

        "방을 나간 멤버에게는 남기지 않는다 — 이탈자는 더 이상 이 방의 수신자가 아니다" {
            val sender = saveMember("산책러버")
            val receiver = saveMember("받는사람")
            val leaver = saveMember("나간사람")
            listOf(sender, receiver).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = it.id))
            }
            chatRoomMemberRepository.save(
                ChatRoomMemberFixture.create(roomId = ROOM, memberId = leaver.id)
                    .apply { leave(LocalDateTime.of(2026, 3, 14, 10, 0)) },
            )

            chatMessageNotifier.notifyNewMessage(message(sender.id)) shouldBe 1

            notificationRepository.findAll().single().memberId shouldBe receiver.id
        }

        "이미지 메시지는 미리보기 문구로 바꾼다 — 본문에 S3 key 가 들어가면 안 된다" {
            val sender = saveMember("산책러버")
            val receiver = saveMember("받는사람")
            listOf(sender, receiver).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = it.id))
            }

            chatMessageNotifier.notifyNewMessage(
                message(sender.id, content = "chat/${sender.id}/uuid", messageType = ChatMessageType.IMAGE),
            )

            notificationRepository.findAll().single().body shouldBe "사진을 보냈어요."
        }

        "연속으로 보내도 방당 한 줄로 접힌다 — 알림 센터가 메시지 목록이 되면 안 된다" {
            val sender = saveMember("산책러버")
            val receiver = saveMember("받는사람")
            listOf(sender, receiver).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = it.id))
            }

            chatMessageNotifier.notifyNewMessage(message(sender.id, content = "첫 메시지"))
            chatMessageNotifier.notifyNewMessage(message(sender.id, content = "두 번째 메시지"))

            val notifications = notificationRepository.findAll()
            notifications.size shouldBe 1
            notifications.single().body shouldBe "두 번째 메시지"
        }

        "받을 사람이 없으면 아무 것도 하지 않는다" {
            val sender = saveMember("혼자")
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = sender.id))

            chatMessageNotifier.notifyNewMessage(message(sender.id)) shouldBe 0
        }

        "보낸 사람을 찾을 수 없으면 알리지 않는다 — 이름이 빈 문구를 만들지 않는다" {
            val receiver = saveMember("받는사람")
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = receiver.id))

            chatMessageNotifier.notifyNewMessage(message(senderId = 9999L)) shouldBe 0

            notificationRepository.count() shouldBe 0
        }
    }
})
