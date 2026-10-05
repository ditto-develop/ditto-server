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
import org.springframework.transaction.annotation.Transactional

/** 퀴즈셋과 그 셋에 딸린 행을 FK 없이 직접 지운다. 삭제 조건과 범위는 docs/domains/quiz.md 에 있다. */
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

    @Transactional
    fun delete(quizSetId: Long) {
        if (!quizSetRepository.existsById(quizSetId)) {
            throw WarnException(ErrorCode.NOT_FOUND)
        }
        if (hasMatchRecords(quizSetId)) {
            throw WarnException(
                ErrorCode.BAD_REQUEST,
                "매칭이 진행된 퀴즈셋은 삭제할 수 없습니다. 그 주 매칭·채팅·평가가 이 퀴즈셋을 기준으로 남아 있습니다.",
            )
        }
        deleteMatchResultNotifications(quizSetId)
        deleteQuizzesWithAnswers(quizSetId)
        quizProgressRepository.deleteByQuizSetId(quizSetId)
        quizSetRepository.deleteById(quizSetId)
    }

    fun hasMatchRecords(quizSetId: Long): Boolean =
        matchCandidateRepository.existsByQuizSetId(quizSetId) ||
            personalMatchRepository.existsByQuizSetId(quizSetId) ||
            groupMatchRepository.existsByQuizSetId(quizSetId)

    /** 매칭 결과 알림만 지우고 지운 수를 돌려준다. 퀴즈 열림·마감 알림은 그 주 대표 셋을 가리킬 뿐이라 남긴다. */
    fun deleteMatchResultNotifications(quizSetId: Long): Int {
        val typesReadingQuizSet = NotificationType.pointingTo(NotificationTarget.QUIZ_SET)
            .filter { it.deepLinkTarget.readsTargetRow }
        val notificationIds = notificationRepository
            .findByTypeInAndTargetIdIn(typesReadingQuizSet, listOf(quizSetId))
            .map { it.id }
        if (notificationIds.isEmpty()) return 0
        notificationRepository.deleteAllByIdInBatch(notificationIds)
        return notificationIds.size
    }

    private fun deleteQuizzesWithAnswers(quizSetId: Long) {
        val quizIds = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId).map { it.id }
        if (quizIds.isEmpty()) return
        quizAnswerRepository.deleteByQuizIdIn(quizIds)
        quizChoiceRepository.deleteByQuizIdIn(quizIds)
        quizRepository.deleteByQuizSetId(quizSetId)
    }
}
