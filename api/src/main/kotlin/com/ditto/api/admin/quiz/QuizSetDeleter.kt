package com.ditto.api.admin.quiz

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationTarget
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizChoiceStatRepository
import com.ditto.domain.quiz.repository.QuizMemberStatRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.quiz.repository.QuizSetStatRepository
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
    private val quizSetStatRepository: QuizSetStatRepository,
    private val quizChoiceStatRepository: QuizChoiceStatRepository,
    private val quizMemberStatRepository: QuizMemberStatRepository,
) {

    @Transactional
    fun delete(quizSetId: Long) {
        if (!quizSetRepository.existsById(quizSetId)) {
            throw WarnException(ErrorCode.NOT_FOUND, "없는 퀴즈셋입니다: #$quizSetId")
        }
        if (hasMatchRecords(quizSetId)) {
            throw WarnException(
                ErrorCode.BAD_REQUEST,
                "매칭이 진행된 퀴즈셋은 삭제할 수 없습니다. 그 주 매칭·채팅·평가가 이 퀴즈셋을 기준으로 남아 있습니다.",
            )
        }
        deleteMatchResultNotifications(quizSetId)
        deleteStatSnapshot(quizSetId)
        deleteQuizzesWithAnswers(quizSetId)
        quizProgressRepository.deleteByQuizSetId(quizSetId)
        quizSetRepository.deleteById(quizSetId)
    }

    fun hasMatchRecords(quizSetId: Long): Boolean =
        matchCandidateRepository.existsByQuizSetId(quizSetId) ||
            personalMatchRepository.existsByQuizSetId(quizSetId) ||
            groupMatchRepository.existsByQuizSetId(quizSetId)

    /** 매칭 결과 알림만 지우고 지운 수를 돌려준다. 퀴즈 열림·마감 알림은 그 주 대표 셋을 가리킬 뿐이라 남긴다. */
    fun deleteMatchResultNotifications(quizSetId: Long): Int =
        deleteNotifications(findMatchResultNotifications(quizSetId))

    fun deleteMemberMatchResultNotifications(quizSetId: Long, memberId: Long): Int =
        deleteNotifications(findMatchResultNotifications(quizSetId).filter { it.memberId == memberId })

    private fun findMatchResultNotifications(quizSetId: Long): List<Notification> {
        val typesReadingQuizSet = NotificationType.pointingTo(NotificationTarget.QUIZ_SET)
            .filter { it.deepLinkTarget.readsTargetRow }
        return notificationRepository.findByTypeInAndTargetIdIn(typesReadingQuizSet, listOf(quizSetId))
    }

    private fun deleteNotifications(notifications: List<Notification>): Int {
        if (notifications.isEmpty()) return 0
        notificationRepository.deleteAllByIdInBatch(notifications.map { it.id })
        return notifications.size
    }

    /**
     * 마감 통계 스냅샷. 매칭 전에 마감된 셋(후보 0건 등)이면 이미 만들어져 있을 수 있다.
     * 답을 지운 뒤(QA 초기화)에도 부른다 — 남기면 지운 답이 숫자에 남는다. 다음 조회가 다시 만든다.
     */
    fun deleteStatSnapshot(quizSetId: Long) {
        quizMemberStatRepository.deleteAllByIdInBatch(quizMemberStatRepository.findByQuizSetId(quizSetId).map { it.id })
        quizChoiceStatRepository.deleteAllByIdInBatch(quizChoiceStatRepository.findByQuizSetId(quizSetId).map { it.id })
        quizSetStatRepository.findByQuizSetId(quizSetId)?.let { quizSetStatRepository.delete(it) }
    }

    private fun deleteQuizzesWithAnswers(quizSetId: Long) {
        val quizIds = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId).map { it.id }
        if (quizIds.isEmpty()) return
        quizAnswerRepository.deleteByQuizIdIn(quizIds)
        quizChoiceRepository.deleteByQuizIdIn(quizIds)
        quizRepository.deleteByQuizSetId(quizSetId)
    }
}
