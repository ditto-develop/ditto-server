package com.ditto.api.notification.push

import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatRoom
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
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.util.Optional

private const val MEMBER_ID = 1L

/** 알림 한 행이 어떤 푸시가 되는지. 발송은 [PushSender] mock 으로 끊는다. */
class PushNotifierTest : FreeSpec({

    data class Fixture(
        val notifier: PushNotifier,
        val pushSender: PushSender,
        val cleaner: PushDeadDeviceCleaner,
        val chatRoomRepository: ChatRoomRepository,
    )

    fun fixture(
        setting: MemberNotificationSetting? = null,
        deviceTokens: List<String> = listOf("token-1"),
        room: ChatRoom? = null,
        unreadCount: Long = 3L,
    ): Fixture {
        val settingRepository = mockk<MemberNotificationSettingRepository> {
            every { findByMemberId(any()) } returns setting
        }
        val deviceRepository = mockk<MemberDeviceRepository> {
            every { findAllByMemberId(any()) } answers {
                deviceTokens.map { MemberDeviceFixture.create(memberId = firstArg(), token = it) }
            }
        }
        val notificationRepository = mockk<NotificationRepository> {
            every { countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(any(), any()) } returns unreadCount
        }
        val chatRoomRepository = mockk<ChatRoomRepository> {
            every { findById(any()) } returns Optional.ofNullable(room)
        }
        val cleaner = mockk<PushDeadDeviceCleaner>(relaxed = true)
        val pushSender = mockk<PushSender>(relaxed = true)
        val notifier = PushNotifier(
            memberNotificationSettingRepository = settingRepository,
            memberDeviceRepository = deviceRepository,
            notificationRepository = notificationRepository,
            chatRoomRepository = chatRoomRepository,
            pushDeadDeviceCleaner = cleaner,
            pushSender = pushSender,
        )
        return Fixture(notifier, pushSender, cleaner, chatRoomRepository)
    }

    fun notification(
        type: NotificationType,
        targetId: Long? = 100L,
        memberId: Long = MEMBER_ID,
        id: Long = 8821L,
    ) = NotificationFixture.create(memberId = memberId, type = type, targetId = targetId, id = id)

    "토글 게이트" - {
        "채팅 알림을 끈 회원에게는 CHAT 푸시가 나가지 않는다" {
            val (notifier, pushSender, _, _) = fixture(
                setting = MemberNotificationSetting(memberId = MEMBER_ID, matching = true, chat = false, marketing = false),
            )

            notifier.pushAll(listOf(notification(NotificationType.CHAT_MESSAGE)))

            verify(exactly = 0) { pushSender.send(any(), any()) }
        }

        "설정 행이 없으면 기본값으로 판단한다 — 매칭·채팅은 기본 수신이라 나간다" {
            val (notifier, pushSender, _, _) = fixture(setting = null, room = ChatRoomFixture.personal())

            notifier.pushAll(listOf(notification(NotificationType.CHAT_MESSAGE)))

            verify(exactly = 1) { pushSender.send(any(), any()) }
        }

        "SYSTEM 은 마케팅 토글이 꺼져 있어도 나간다 — 공지는 마케팅 동의와 다른 개념이다" {
            val (notifier, pushSender, _, _) = fixture(
                setting = MemberNotificationSetting(memberId = MEMBER_ID, matching = false, chat = false, marketing = false),
            )

            notifier.pushAll(listOf(notification(NotificationType.SYSTEM_NOTICE, targetId = null)))

            verify(exactly = 1) { pushSender.send(any(), any()) }
        }
    }

    "기기가 없으면 보내지 않는다 — 웹 전용 회원" {
        val (notifier, pushSender, _, _) = fixture(deviceTokens = emptyList())

        notifier.pushAll(listOf(notification(NotificationType.CHAT_MESSAGE)))

        verify(exactly = 0) { pushSender.send(any(), any()) }
    }

    "payload" - {
        fun sentMessage(type: NotificationType, room: ChatRoom? = null, targetId: Long? = 100L): PushMessage {
            val (notifier, pushSender, _, _) = fixture(room = room)
            val messageSlot = slot<PushMessage>()
            every { pushSender.send(capture(messageSlot), any()) } returns Unit

            notifier.pushAll(listOf(notification(type, targetId = targetId)))

            return messageSlot.captured
        }

        "data 값은 전부 문자열이고, 뱃지는 미읽음 수다" {
            val message = sentMessage(NotificationType.CHAT_MESSAGE, room = ChatRoomFixture.group())

            message.data["notificationId"] shouldBe "8821"
            message.data["type"] shouldBe "CHAT_MESSAGE"
            message.unreadCount shouldBe 3
            message.tokens shouldBe listOf("token-1")
        }

        "deepLink — 채팅 계열은 방 종류로 경로가 갈린다" {
            sentMessage(NotificationType.CHAT_MESSAGE, room = ChatRoomFixture.group())
                .data["deepLink"] shouldBe "/chat/group/100/"
            sentMessage(NotificationType.CHAT_MESSAGE, room = ChatRoomFixture.personal())
                .data["deepLink"] shouldBe "/chat/one-on-one/100/"
            // 재매칭 방도 1:1 화면이다 — FE 방 목록과 같은 이분법.
            sentMessage(NotificationType.CHAT_ENDING_SOON, room = ChatRoomFixture.rematch())
                .data["deepLink"] shouldBe "/chat/one-on-one/100/"
        }

        "deepLink — 평가 요청은 방 경로 밑의 rate 다" {
            sentMessage(NotificationType.REVIEW_REQUEST, room = ChatRoomFixture.personal())
                .data["deepLink"] shouldBe "/chat/one-on-one/100/rate/"
        }

        "deepLink — 유형이 종류를 내포하면 방을 조회하지 않는다" {
            sentMessage(NotificationType.MATCH_RESULT).data["deepLink"] shouldBe "/matching/"
            sentMessage(NotificationType.GROUP_FORMED).data["deepLink"] shouldBe "/chat/group/100/"
            sentMessage(NotificationType.VOTE_CLOSED).data["deepLink"] shouldBe "/chat/group/100/"
            sentMessage(NotificationType.REMATCH_MATCHED).data["deepLink"] shouldBe "/chat/one-on-one/100/"
        }

        "deepLink — 방이 지워졌으면 deepLink 없이 보낸다 (탭하면 앱만 열림)" {
            val message = sentMessage(NotificationType.CHAT_MESSAGE, room = null)

            message.data.containsKey("deepLink") shouldBe false
            message.data["notificationId"] shouldBe "8821"
        }

        "deepLink — SYSTEM 공지는 이동할 곳이 없다" {
            sentMessage(NotificationType.SYSTEM_NOTICE, targetId = null).data.containsKey("deepLink") shouldBe false
        }

        "ttl — 시효가 있는 알림에만 준다" {
            sentMessage(NotificationType.CHAT_MESSAGE, room = ChatRoomFixture.personal())
                .ttl shouldBe Duration.ofHours(1)
            sentMessage(NotificationType.CHAT_ENDING_SOON, room = ChatRoomFixture.personal())
                .ttl shouldBe Duration.ofHours(6)
            sentMessage(NotificationType.MATCH_RESULT).ttl shouldBe null
        }
    }

    "같은 사건의 알림 여러 건이면 방 조회는 한 번이다 — 그룹 채팅 수신자 수만큼 반복하지 않는다" {
        val (notifier, pushSender, _, chatRoomRepository) = fixture(room = ChatRoomFixture.group())

        notifier.pushAll(
            listOf(
                notification(NotificationType.CHAT_MESSAGE, memberId = 1L),
                notification(NotificationType.CHAT_MESSAGE, memberId = 2L),
                notification(NotificationType.CHAT_MESSAGE, memberId = 3L),
            ),
        )

        verify(exactly = 1) { chatRoomRepository.findById(100L) }
        verify(exactly = 3) { pushSender.send(any(), any()) }
    }

    "죽은 토큰 콜백이 오면 정리 빈으로 넘긴다" {
        val (notifier, pushSender, cleaner, _) = fixture(room = ChatRoomFixture.personal())
        val callbackSlot = slot<(List<String>) -> Unit>()
        every { pushSender.send(any(), capture(callbackSlot)) } returns Unit

        notifier.pushAll(listOf(notification(NotificationType.CHAT_MESSAGE)))
        callbackSlot.captured(listOf("dead-token"))

        verify(exactly = 1) { cleaner.clean(listOf("dead-token")) }
    }

    "준비 조회가 실패해도 예외가 밖으로 나가지 않는다" {
        val settingRepository = mockk<MemberNotificationSettingRepository> {
            every { findByMemberId(MEMBER_ID) } throws RuntimeException("db down")
        }
        val notifier = PushNotifier(
            memberNotificationSettingRepository = settingRepository,
            memberDeviceRepository = mockk(),
            notificationRepository = mockk(),
            chatRoomRepository = mockk(),
            pushDeadDeviceCleaner = mockk(),
            pushSender = mockk(),
        )

        shouldNotThrowAny {
            notifier.pushAll(listOf(notification(NotificationType.SYSTEM_NOTICE, targetId = null)))
        }
    }
})
