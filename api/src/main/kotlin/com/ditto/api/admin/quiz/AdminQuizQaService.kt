package com.ditto.api.admin.quiz

import com.ditto.api.admin.cleanup.MatchingRecordEraser
import com.ditto.api.admin.cleanup.MatchingRecordTargets
import com.ditto.api.admin.quiz.dto.MatchingErasePreview
import com.ditto.api.admin.quiz.dto.MatchingEraseSummary
import com.ditto.api.admin.quiz.dto.MatchingRecordCounts
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * QA 를 반복할 수 있게 매칭이 끝난 퀴즈셋의 매칭 기록을 지운다. 운영 데이터가 지워지므로 QA 도구 스위치가
 * 켜져 있을 때만 동작한다. 신고·제재는 회원 기록이라 남긴다.
 */
@Service
@Transactional
class AdminQuizQaService(
    private val qaToolGuard: QaToolGuard,
    private val quizSetRepository: QuizSetRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val quizSetMatchingTargetFinder: QuizSetMatchingTargetFinder,
    private val matchingRecordEraser: MatchingRecordEraser,
    private val quizSetDeleter: QuizSetDeleter,
) {
    @Transactional(readOnly = true)
    fun previewErase(quizSetId: Long): MatchingErasePreview {
        val targets = quizSetMatchingTargetFinder.findTargetsOf(quizSetId)
        return MatchingErasePreview(
            counts = countsOf(targets, matchCandidateRepository.countByQuizSetId(quizSetId)),
            realMemberRoomCount = quizSetMatchingTargetFinder.findRoomIdsWithRealMembers(targets.roomIds).size,
        )
    }

    /** 퀴즈셋·답·진행은 남기고 매칭 기록만 지워 같은 셋으로 매칭을 다시 돌릴 수 있게 한다. */
    fun resetMatching(quizSetId: Long): MatchingEraseSummary {
        qaToolGuard.validateEnabled()
        val quizSet = findQuizSet(quizSetId)
        qaToolGuard.validateCurrentWeek(quizSet)
        return eraseMatchingRecordsOf(quizSet)
    }

    /** 매칭 기록을 먼저 지워 일반 삭제가 막히지 않게 한 뒤 퀴즈셋까지 지운다. */
    fun forceDelete(quizSetId: Long): MatchingEraseSummary {
        qaToolGuard.validateEnabled()
        val summary = eraseMatchingRecordsOf(findQuizSet(quizSetId))
        // 일반 삭제가 매칭 결과 알림을 한 번 더 지우지만 이미 비어 있어 0건이다.
        quizSetDeleter.delete(quizSetId)
        return summary
    }

    private fun eraseMatchingRecordsOf(quizSet: QuizSet): MatchingEraseSummary {
        val targets = quizSetMatchingTargetFinder.findTargetsOf(quizSet.id)
        val candidateRowCount = matchCandidateRepository.deleteByQuizSetId(quizSet.id)
        val matchingNotificationCount = matchingRecordEraser.erase(targets)
        val matchResultNotificationCount = quizSetDeleter.deleteMatchResultNotifications(quizSet.id)
        return MatchingEraseSummary(
            quizSetId = quizSet.id,
            quizSetTitle = quizSet.title,
            counts = countsOf(targets, candidateRowCount),
            notificationCount = matchingNotificationCount + matchResultNotificationCount,
        )
    }

    private fun countsOf(targets: MatchingRecordTargets, candidateRowCount: Int): MatchingRecordCounts =
        MatchingRecordCounts(
            candidateRowCount = candidateRowCount,
            personalMatchCount = targets.personalMatchIds.size,
            groupMatchCount = targets.groupMatchIds.size,
            rematchCount = targets.rematchIds.size,
            roomCount = targets.roomIds.size,
        )

    private fun findQuizSet(quizSetId: Long): QuizSet =
        quizSetRepository.findById(quizSetId).orElseThrow { WarnException(ErrorCode.NOT_FOUND, "없는 퀴즈셋입니다: #$quizSetId") }
}
