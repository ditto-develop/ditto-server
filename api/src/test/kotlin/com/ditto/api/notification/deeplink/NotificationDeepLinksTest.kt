package com.ditto.api.notification.deeplink

import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.repository.RematchRepository
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

/** 경로 규칙은 PushNotifierTest 가 푸시로 검증한다. 여기서는 목록용 일괄 조회를 본다. */
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

        val deepLinkById = notificationDeepLinks.deepLinksByNotificationId(
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

    "모든 유형에서 푸시용 단건 조회와 목록용 일괄 조회가 같은 경로를 낸다" {
        val room = ChatRoomFixture.group(id = 10L)
        val quizSet = QuizSetFixture.create(matchingType = MatchingType.GROUP, id = 10L)
        val pair = RematchFixture.create(sourceChatRoomId = 55L, id = 10L)
        every { chatRoomRepository.findById(10L) } returns Optional.of(room)
        every { quizSetRepository.findById(10L) } returns Optional.of(quizSet)
        every { rematchRepository.findById(10L) } returns Optional.of(pair)
        every { chatRoomRepository.findAllById(any<Iterable<Long>>()) } returns listOf(room)
        every { quizSetRepository.findAllById(any<Iterable<Long>>()) } returns listOf(quizSet)
        every { rematchRepository.findAllById(any<Iterable<Long>>()) } returns listOf(pair)

        NotificationType.entries.forEach { type ->
            val notification = notification(type, targetId = 10L)

            val batch = notificationDeepLinks.deepLinksByNotificationId(listOf(notification))[notification.id]

            withClue(type) { batch shouldBe notificationDeepLinks.deepLinkFor(notification) }
        }
    }

    "일괄 조회에서 대상이 사라졌거나 없으면 null 이다" {
        every { chatRoomRepository.findAllById(any<Iterable<Long>>()) } returns emptyList()
        every { quizSetRepository.findAllById(any<Iterable<Long>>()) } returns emptyList()
        every { rematchRepository.findAllById(any<Iterable<Long>>()) } returns emptyList()
        val missingQuizSet = notification(NotificationType.MATCH_RESULT, targetId = 1L)
        val missingPair = notification(NotificationType.REMATCH_REQUESTED, targetId = 2L)
        val noTargetRoom = notification(NotificationType.GROUP_FORMED, targetId = null)

        val deepLinkById = notificationDeepLinks.deepLinksByNotificationId(
            listOf(missingQuizSet, missingPair, noTargetRoom),
        )

        deepLinkById[missingQuizSet.id] shouldBe null
        deepLinkById[missingPair.id] shouldBe null
        deepLinkById[noTargetRoom.id] shouldBe null
    }

    "조회가 필요 없는 유형만 있으면 아무것도 조회하지 않는다" {
        val matchRequest = notification(NotificationType.MATCH_REQUESTED, targetId = 1L)
        val quizOpened = notification(NotificationType.QUIZ_OPENED, targetId = 2L)
        val groupFormed = notification(NotificationType.GROUP_FORMED, targetId = 3L)

        val deepLinkById = notificationDeepLinks.deepLinksByNotificationId(
            listOf(matchRequest, quizOpened, groupFormed),
        )

        deepLinkById[matchRequest.id] shouldBe "/matching/"
        deepLinkById[quizOpened.id] shouldBe "/quiz/current/"
        deepLinkById[groupFormed.id] shouldBe "/chat/group/3/"
        verify(exactly = 0) { chatRoomRepository.findAllById(any<Iterable<Long>>()) }
        verify(exactly = 0) { quizSetRepository.findAllById(any<Iterable<Long>>()) }
        verify(exactly = 0) { rematchRepository.findAllById(any<Iterable<Long>>()) }
    }
})
