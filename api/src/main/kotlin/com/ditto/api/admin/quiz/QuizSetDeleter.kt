package com.ditto.api.admin.quiz

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.notification.entity.NotificationTarget
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Component

/**
 * 퀴즈셋을 문항·선택지·답변·진행, 셋을 가리키는 알림과 함께 지운다. FK 가 없어 직접 정리한다.
 * 매칭이 돈 셋은 그 주 매칭·채팅·평가·재매칭이 셋을 기준으로 남아 있어 지우지 않는다.
 * 평가·재매칭은 매칭에서만 생기므로 매칭 기록만 본다.
 */
@Component
class QuizSetDeleter(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val notificationRepository: NotificationRepository,
) {

    fun delete(quizSetId: Long) {
        if (hasMatching(quizSetId)) {
            throw WarnException(
                ErrorCode.BAD_REQUEST,
                "매칭이 진행된 퀴즈셋은 삭제할 수 없습니다. 그 주 매칭·채팅·평가가 이 퀴즈셋을 기준으로 남아 있습니다.",
            )
        }
        deleteNotificationsPointingTo(quizSetId)
        deleteQuizzesWithAnswers(quizSetId)
        quizProgressRepository.deleteByQuizSetId(quizSetId)
        quizSetRepository.deleteById(quizSetId)
    }

    fun hasMatching(quizSetId: Long): Boolean =
        matchCandidateRepository.existsByQuizSetId(quizSetId) ||
            personalMatchRepository.existsByQuizSetId(quizSetId) ||
            groupMatchRepository.existsByQuizSetId(quizSetId)

    private fun deleteNotificationsPointingTo(quizSetId: Long) {
        val notificationIds = notificationRepository
            .findByTypeInAndTargetIdIn(NotificationType.pointingTo(NotificationTarget.QUIZ_SET), listOf(quizSetId))
            .map { it.id }
        if (notificationIds.isEmpty()) return
        notificationRepository.deleteAllByIdInBatch(notificationIds)
    }

    private fun deleteQuizzesWithAnswers(quizSetId: Long) {
        val quizIds = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId).map { it.id }
        if (quizIds.isEmpty()) return
        quizAnswerRepository.deleteByQuizIdIn(quizIds)
        quizChoiceRepository.deleteByQuizIdIn(quizIds)
        quizRepository.deleteByQuizSetId(quizSetId)
    }
}
