package com.ditto.api.notification

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.match.service.MatchmakingService
import com.ditto.api.notification.notifier.ChatEndingSoonNotifier
import com.ditto.api.notification.notifier.ChatMessageNotifier
import com.ditto.api.notification.notifier.ChatNoMessageNotifier
import com.ditto.api.notification.notifier.ChatRoomOpenedNotifier
import com.ditto.api.notification.notifier.MatchResultNotifier
import com.ditto.api.notification.notifier.QuizNotifier
import com.ditto.api.notification.notifier.RematchNotifier
import com.ditto.api.notification.notifier.ReviewReminderNotifier
import com.ditto.api.notification.notifier.ReviewRequestNotifier
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.repository.MemberReviewRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException

/**
 * 알림을 남기려다 실패해도 부르는 쪽 흐름이 끊기지 않는지 본다.
 *
 * 적재 자체의 실패는 `NotificationAppender`가 흡수하지만([NotificationAppenderTest]), 그 앞의 조회는
 * 흡수 범위 밖이라 각 Notifier 가 스스로 막는다. 조회 실패는 실제 입력으로 만들 수 없어 mock 을 쓴다.
 */
class NotifierFailureTest {

    private val chatRoomRepository = mockk<ChatRoomRepository>()
    private val chatRoomMemberRepository = mockk<ChatRoomMemberRepository>()
    private val memberRepository = mockk<MemberRepository>()
    private val matchCandidateRepository = mockk<MatchCandidateRepository>()
    private val groupMatchRepository = mockk<GroupMatchRepository>()
    private val groupMatchMemberRepository = mockk<GroupMatchMemberRepository>()
    private val matchmakingService = mockk<MatchmakingService>()
    private val notificationRepository = mockk<NotificationRepository>()
    private val quizSetRepository = mockk<QuizSetRepository>()
    private val quizRepository = mockk<QuizRepository>()
    private val quizProgressRepository = mockk<QuizProgressRepository>()
    private val memberReviewRepository = mockk<MemberReviewRepository>()
    private val rematchRepository = mockk<RematchRepository>()
    private val notificationAppender = mockk<NotificationAppender>(relaxed = true)

    private val reviewRequestNotifier = ReviewRequestNotifier(
        chatRoomRepository,
        chatRoomMemberRepository,
        memberRepository,
        notificationAppender,
    )
    private val chatMessageNotifier = ChatMessageNotifier(
        chatRoomMemberRepository,
        memberRepository,
        notificationAppender,
    )
    private val matchResultNotifier = MatchResultNotifier(
        matchCandidateRepository,
        groupMatchRepository,
        groupMatchMemberRepository,
        matchmakingService,
        notificationRepository,
        notificationAppender,
    )
    private val chatEndingSoonNotifier = ChatEndingSoonNotifier(
        chatRoomRepository,
        chatRoomMemberRepository,
        notificationAppender,
        LEAD_HOURS,
    )
    private val chatRoomOpenedNotifier = ChatRoomOpenedNotifier(chatRoomMemberRepository, notificationAppender)
    private val chatNoMessageNotifier = ChatNoMessageNotifier(
        chatRoomRepository,
        chatRoomMemberRepository,
        notificationAppender,
        LEAD_HOURS,
    )
    private val reviewReminderNotifier = ReviewReminderNotifier(memberReviewRepository, notificationAppender)
    private val rematchNotifier = RematchNotifier(
        memberReviewRepository,
        rematchRepository,
        memberRepository,
        notificationAppender,
    )
    private val quizNotifier = QuizNotifier(
        quizSetRepository,
        quizRepository,
        quizProgressRepository,
        memberRepository,
        notificationAppender,
    )

    @Test
    @DisplayName("평가 요청 — 방 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun reviewRequestAbsorbsRoomQueryFailure() {
        every { chatRoomRepository.findAllById(any()) } throws connectionFailure()

        reviewRequestNotifier.notifyFor(listOf(ROOM_ID)) shouldBe 0
    }

    // 조회가 여럿이라 마지막 것까지 감싸였는지 따로 본다.
    @Test
    @DisplayName("평가 요청 — 닉네임 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun reviewRequestAbsorbsNicknameQueryFailure() {
        val room = ChatRoomFixture.personal()
        every { chatRoomRepository.findAllById(any()) } returns listOf(room)
        every { chatRoomMemberRepository.findByRoomIdIn(any()) } returns listOf(
            ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L),
            ChatRoomMemberFixture.create(roomId = room.id, memberId = 2L),
        )
        every { memberRepository.findAllById(any()) } throws connectionFailure()

        reviewRequestNotifier.notifyFor(listOf(room.id)) shouldBe 0
    }

    @Test
    @DisplayName("새 메시지 — 참여자 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun chatMessageAbsorbsMemberQueryFailure() {
        every { chatRoomMemberRepository.findByRoomIdIn(any()) } throws connectionFailure()

        chatMessageNotifier.notifyNewMessage(textMessage()) shouldBe 0

        verify(exactly = 0) { notificationAppender.appendAll(any(), any(), any()) }
    }

    @Test
    @DisplayName("매칭 결과 — 후보 회원 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun matchResultAbsorbsCandidateQueryFailure() {
        every { matchCandidateRepository.findOwnerMemberIdsByQuizSetId(any()) } throws connectionFailure()

        matchResultNotifier.notifyFor(listOf(QUIZ_SET_ID)) shouldBe 0
    }

    @Test
    @DisplayName("종료 임박 — 방 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun chatEndingSoonAbsorbsRoomQueryFailure() {
        every { chatRoomRepository.findAllIdsEndingBetween(any(), any()) } throws connectionFailure()

        chatEndingSoonNotifier.notifyEndingSoon(LocalDateTime.of(2026, 7, 16, 12, 0)) shouldBe 0
    }

    @Test
    @DisplayName("채팅방 오픈 — 참여자 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun chatRoomOpenedAbsorbsMemberQueryFailure() {
        every { chatRoomMemberRepository.findByRoomIdIn(any()) } throws connectionFailure()

        chatRoomOpenedNotifier.notifyOpened(listOf(ROOM_ID)) shouldBe 0
    }

    @Test
    @DisplayName("퀴즈 오픈 — 퀴즈셋 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun quizOpenedAbsorbsQuizSetQueryFailure() {
        every { quizSetRepository.findCurrentWeekActive(any()) } throws connectionFailure()

        quizNotifier.notifyOpened(LocalDateTime.of(2026, 7, 13, 0, 0)) shouldBe 0
    }

    @Test
    @DisplayName("퀴즈 마감 임박 — 퀴즈셋 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun quizClosingSoonAbsorbsQuizSetQueryFailure() {
        every { quizSetRepository.findCurrentWeekActive(any()) } throws connectionFailure()

        quizNotifier.notifyClosingSoon(LocalDateTime.of(2026, 7, 15, 18, 0)) shouldBe 0
    }

    @Test
    @DisplayName("첫 메시지 리마인드 — 방 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun chatNoMessageAbsorbsRoomQueryFailure() {
        every { chatRoomRepository.findAllIdsSilentOpenedBetween(any(), any()) } throws connectionFailure()

        chatNoMessageNotifier.notifyNoMessage(LocalDateTime.of(2026, 7, 17, 14, 0)) shouldBe 0
    }

    @Test
    @DisplayName("평가 리마인드 — 평가 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun reviewReminderAbsorbsReviewQueryFailure() {
        every { memberReviewRepository.findPendingAvailableBetween(any(), any()) } throws connectionFailure()

        reviewReminderNotifier.notifyPending(LocalDateTime.of(2026, 7, 20, 9, 0)) shouldBe 0
    }

    @Test
    @DisplayName("재매칭 제출 — 평가 조회가 실패해도 예외 대신 false 를 돌려준다")
    fun rematchSubmittedAbsorbsReviewQueryFailure() {
        every { memberReviewRepository.findById(any()) } throws connectionFailure()

        rematchNotifier.notifySubmitted(reviewId = 1L, submitterId = 1L, counterpartId = 2L) shouldBe false
    }

    private fun connectionFailure() = DataAccessResourceFailureException("커넥션을 얻지 못했습니다")

    private fun textMessage() = ChatMessageResponse(
        id = 10L,
        roomId = ROOM_ID,
        senderId = 2L,
        messageType = ChatMessageType.TEXT,
        content = "안녕",
        imageUrl = null,
        createdAt = LocalDateTime.of(2026, 7, 16, 12, 0),
        unreadCount = 1,
    )

    companion object {
        private const val ROOM_ID = 1L
        private const val QUIZ_SET_ID = 7L
        private const val LEAD_HOURS = 6L
    }
}
