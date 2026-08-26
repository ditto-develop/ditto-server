package com.ditto.api.notification

import com.ditto.api.notification.push.PushDeadDeviceCleaner
import com.ditto.api.notification.push.PushNotifier
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.entity.MemberNotificationSetting
import com.ditto.domain.member.repository.MemberNotificationSettingRepository
import com.ditto.domain.notification.MemberDeviceFixture
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.infrastructure.fcm.PushMessage
import com.ditto.infrastructure.fcm.PushSender
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

private const val ME = 1L

/** 적재된 알림 한 행이 어떤 푸시가 되는지 — 게이트·payload·deepLink. 발송 자체는 [PushSender] mock 으로 끊는다. */
class PushNotifierTest : FreeSpec({

    fun fixture(
        setting: MemberNotificationSetting? = null,
        deviceTokens: List<String> = listOf("token-1"),
        roomType: ChatRoomType? = null,
        unreadCount: Long = 3L,
    ): Pair<PushNotifier, Triple<PushSender, PushDeadDeviceCleaner, ChatRoomRepository>> {
        val settingRepository = mockk<MemberNotificationSettingRepository> {
            every { findByMemberId(ME) } returns setting
        }
        val deviceRepository = mockk<MemberDeviceRepository> {
            every { findAllByMemberId(ME) } returns deviceTokens.map { MemberDeviceFixture.create(memberId = ME, token = it) }
        }
        val notificationRepository = mockk<NotificationRepository> {
            every { countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(ME, any()) } returns unreadCount
        }
        val chatRoomRepository = mockk<ChatRoomRepository> {
            every { findById(any()) } returns Optional.ofNullable(
                when (roomType) {
                    ChatRoomType.PERSONAL -> ChatRoomFixture.personal()
                    ChatRoomType.GROUP -> ChatRoomFixture.group()
                    ChatRoomType.REMATCH -> ChatRoomFixture.rematch()
                    null -> null
                },
            )
        }
        val cleaner = mockk<PushDeadDeviceCleaner>(relaxed = true)
        val pushSender = mockk<PushSender>(relaxed = true)
        val notifier = PushNotifier(
            settingRepository, deviceRepository, notificationRepository, chatRoomRepository, cleaner, pushSender,
        )
        return notifier to Triple(pushSender, cleaner, chatRoomRepository)
    }

    fun notification(type: NotificationType, targetId: Long? = 100L, id: Long = 8821L) =
        NotificationFixture.create(memberId = ME, type = type, targetId = targetId, id = id)

    "토글 게이트" - {
        "채팅 알림을 끈 회원에게는 CHAT 푸시가 나가지 않는다" {
            val (notifier, deps) = fixture(
                setting = MemberNotificationSetting(memberId = ME, matching = true, chat = false, marketing = false),
            )

            notifier.push(notification(NotificationType.CHAT_MESSAGE))

            verify(exactly = 0) { deps.first.send(any(), any()) }
        }

        "설정 행이 없으면 기본값으로 판단한다 — 매칭·채팅은 기본 수신이라 나간다" {
            val (notifier, deps) = fixture(setting = null, roomType = ChatRoomType.PERSONAL)

            notifier.push(notification(NotificationType.CHAT_MESSAGE))

            verify(exactly = 1) { deps.first.send(any(), any()) }
        }

        "SYSTEM 은 마케팅 토글이 꺼져 있어도 나간다 — 공지는 마케팅 동의와 다른 개념이다" {
            val (notifier, deps) = fixture(
                setting = MemberNotificationSetting(memberId = ME, matching = false, chat = false, marketing = false),
            )

            notifier.push(notification(NotificationType.SYSTEM_NOTICE, targetId = null))

            verify(exactly = 1) { deps.first.send(any(), any()) }
        }
    }

    "기기가 없으면 보내지 않는다 — 웹 전용 회원" {
        val (notifier, deps) = fixture(deviceTokens = emptyList())

        notifier.push(notification(NotificationType.CHAT_MESSAGE))

        verify(exactly = 0) { deps.first.send(any(), any()) }
    }

    "payload" - {
        fun sentMessage(type: NotificationType, roomType: ChatRoomType? = null, targetId: Long? = 100L): PushMessage {
            val (notifier, deps) = fixture(roomType = roomType)
            val messageSlot = slot<PushMessage>()
            every { deps.first.send(capture(messageSlot), any()) } returns Unit

            notifier.push(notification(type, targetId = targetId))

            return messageSlot.captured
        }

        "data 값은 전부 문자열이고, 뱃지는 미읽음 수다" {
            val message = sentMessage(NotificationType.CHAT_MESSAGE, roomType = ChatRoomType.GROUP)

            message.data["notificationId"] shouldBe "8821"
            message.data["type"] shouldBe "CHAT_MESSAGE"
            message.unreadCount shouldBe 3
            message.tokens shouldBe listOf("token-1")
        }

        "deepLink — 채팅 계열은 방 종류로 경로가 갈린다" {
            sentMessage(NotificationType.CHAT_MESSAGE, roomType = ChatRoomType.GROUP)
                .data["deepLink"] shouldBe "/chat/group/100/"
            sentMessage(NotificationType.CHAT_MESSAGE, roomType = ChatRoomType.PERSONAL)
                .data["deepLink"] shouldBe "/chat/one-on-one/100/"
            // 재매칭 방도 1:1 화면이다 — FE 방 목록과 같은 이분법.
            sentMessage(NotificationType.CHAT_ENDING_SOON, roomType = ChatRoomType.REMATCH)
                .data["deepLink"] shouldBe "/chat/one-on-one/100/"
        }

        "deepLink — 평가 요청은 방 경로 밑의 rate 다" {
            sentMessage(NotificationType.REVIEW_REQUEST, roomType = ChatRoomType.PERSONAL)
                .data["deepLink"] shouldBe "/chat/one-on-one/100/rate/"
        }

        "deepLink — 유형이 종류를 내포하면 방을 조회하지 않는다" {
            sentMessage(NotificationType.MATCH_RESULT).data["deepLink"] shouldBe "/matching/"
            sentMessage(NotificationType.GROUP_FORMED).data["deepLink"] shouldBe "/chat/group/100/"
            sentMessage(NotificationType.VOTE_CLOSED).data["deepLink"] shouldBe "/chat/group/100/"
            sentMessage(NotificationType.REMATCH_MATCHED).data["deepLink"] shouldBe "/chat/one-on-one/100/"
        }

        "deepLink — 방이 지워졌으면 deepLink 없이 보낸다 (탭하면 앱만 열림)" {
            val message = sentMessage(NotificationType.CHAT_MESSAGE, roomType = null)

            message.data.containsKey("deepLink") shouldBe false
            message.data["notificationId"] shouldBe "8821"
        }

        "deepLink — SYSTEM 공지는 이동할 곳이 없다" {
            sentMessage(NotificationType.SYSTEM_NOTICE, targetId = null).data.containsKey("deepLink") shouldBe false
        }
    }

    "죽은 토큰 콜백이 오면 정리 빈으로 넘긴다" {
        val (notifier, deps) = fixture(roomType = ChatRoomType.PERSONAL)
        val callbackSlot = slot<(List<String>) -> Unit>()
        every { deps.first.send(any(), capture(callbackSlot)) } returns Unit

        notifier.push(notification(NotificationType.CHAT_MESSAGE))
        callbackSlot.captured(listOf("dead-token"))

        verify(exactly = 1) { deps.second.clean(listOf("dead-token")) }
    }

    "준비 조회가 실패해도 예외가 밖으로 나가지 않는다" {
        val settingRepository = mockk<MemberNotificationSettingRepository> {
            every { findByMemberId(ME) } throws RuntimeException("db down")
        }
        val notifier = PushNotifier(
            settingRepository, mockk(), mockk(), mockk(), mockk(), mockk(),
        )

        notifier.push(notification(NotificationType.CHAT_MESSAGE))
    }
})
