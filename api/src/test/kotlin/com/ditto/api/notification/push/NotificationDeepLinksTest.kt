package com.ditto.api.notification.push

import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.repository.RematchRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/** 알림 목록 한 페이지의 경로 계산. 경로 규칙 자체는 PushNotifierTest 가 푸시 경로로 검증한다. */
class NotificationDeepLinksTest : FreeSpec({

    val chatRoomRepository = mockk<ChatRoomRepository>()
    val quizSetRepository = mockk<QuizSetRepository>()
    val rematchRepository = mockk<RematchRepository>()
    val notificationDeepLinks = NotificationDeepLinks(chatRoomRepository, quizSetRepository, rematchRepository)

    beforeTest { clearMocks(chatRoomRepository, quizSetRepository, rematchRepository) }

    var notificationSequence = 0L
    fun notification(type: NotificationType, targetId: Long?) =
        NotificationFixture.create(type = type, targetId = targetId, id = ++notificationSequence)

    "한 페이지의 대상을 종류별로 한 번씩만 조회해 경로를 채운다" {
        every { chatRoomRepository.findAllById(any<Iterable<Long>>()) } returns listOf(
            ChatRoomFixture.group(id = 10L),
            ChatRoomFixture.personal(id = 20L),
        )
        every { quizSetRepository.findAllById(any<Iterable<Long>>()) } returns listOf(
            QuizSetFixture.create(matchingType = MatchingType.GROUP, id = 30L),
        )
        every { rematchRepository.findAllById(any<Iterable<Long>>()) } returns listOf(
            RematchFixture.create(sourceChatRoomId = 55L, id = 40L),
        )
        val groupChat = notification(NotificationType.CHAT_MESSAGE, targetId = 10L)
        val sameGroupChat = notification(NotificationType.CHAT_ENDING_SOON, targetId = 10L)
        val personalReview = notification(NotificationType.REVIEW_REQUEST, targetId = 20L)
        val deletedRoom = notification(NotificationType.CHAT_MESSAGE, targetId = 99L)
        val groupWeekResult = notification(NotificationType.MATCH_RESULT, targetId = 30L)
        val rematchRequest = notification(NotificationType.REMATCH_REQUESTED, targetId = 40L)
        val sanction = notification(NotificationType.SANCTION_IMPOSED, targetId = 50L)
        val notice = notification(NotificationType.SYSTEM_NOTICE, targetId = null)

        val deepLinkById = notificationDeepLinks.deepLinksOf(
            listOf(
                groupChat,
                sameGroupChat,
                personalReview,
                deletedRoom,
                groupWeekResult,
                rematchRequest,
                sanction,
                notice,
            ),
        )

        deepLinkById[groupChat.id] shouldBe "/chat/group/10/"
        deepLinkById[sameGroupChat.id] shouldBe "/chat/group/10/"
        deepLinkById[personalReview.id] shouldBe "/chat/one-on-one/20/rate/"
        deepLinkById[deletedRoom.id] shouldBe null
        deepLinkById[groupWeekResult.id] shouldBe "/matching/group/"
        deepLinkById[rematchRequest.id] shouldBe "/chat/group/55/rate/"
        deepLinkById[sanction.id] shouldBe "/sanction/"
        deepLinkById[notice.id] shouldBe null
        verify(exactly = 1) { chatRoomRepository.findAllById(listOf(10L, 20L, 99L)) }
        verify(exactly = 1) { quizSetRepository.findAllById(listOf(30L)) }
        verify(exactly = 1) { rematchRepository.findAllById(listOf(40L)) }
        verify(exactly = 0) { chatRoomRepository.findById(any()) }
    }

    "조회가 필요 없는 유형만 있으면 아무것도 조회하지 않는다" {
        val matchRequest = notification(NotificationType.MATCH_REQUESTED, targetId = 1L)
        val quizOpened = notification(NotificationType.QUIZ_OPENED, targetId = 2L)

        val deepLinkById = notificationDeepLinks.deepLinksOf(listOf(matchRequest, quizOpened))

        deepLinkById[matchRequest.id] shouldBe "/matching/"
        deepLinkById[quizOpened.id] shouldBe "/quiz/current/"
        verify(exactly = 0) { chatRoomRepository.findAllById(any<Iterable<Long>>()) }
        verify(exactly = 0) { quizSetRepository.findAllById(any<Iterable<Long>>()) }
        verify(exactly = 0) { rematchRepository.findAllById(any<Iterable<Long>>()) }
    }
})
