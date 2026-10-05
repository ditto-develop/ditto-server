package com.ditto.api.admin.quiz

import com.ditto.api.admin.cleanup.MatchingRecordEraser
import com.ditto.api.config.AdminQaToolsProperties
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.repository.MatchCandidateRepository
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
    private val adminQaToolsProperties: AdminQaToolsProperties,
    private val quizSetRepository: QuizSetRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val quizSetMatchingTargetFinder: QuizSetMatchingTargetFinder,
    private val matchingRecordEraser: MatchingRecordEraser,
    private val quizSetDeleter: QuizSetDeleter,
) {
    /** 퀴즈셋·답·진행은 남기고 매칭 기록만 지운다. 같은 셋으로 매칭을 다시 돌릴 수 있다. */
    fun resetMatching(quizSetId: Long): MatchingEraseSummary {
        validateQaToolsEnabled()
        validateQuizSetExists(quizSetId)
        return eraseMatchingRecordsOf(quizSetId)
    }

    /** 매칭 기록을 먼저 지워 일반 삭제가 막히지 않게 한 뒤 퀴즈셋까지 지운다. */
    fun forceDelete(quizSetId: Long): MatchingEraseSummary {
        validateQaToolsEnabled()
        validateQuizSetExists(quizSetId)
        val summary = eraseMatchingRecordsOf(quizSetId)
        // 일반 삭제가 매칭 결과 알림을 한 번 더 지우지만 이미 비어 있어 0건이다.
        quizSetDeleter.delete(quizSetId)
        return summary
    }

    private fun eraseMatchingRecordsOf(quizSetId: Long): MatchingEraseSummary {
        val targets = quizSetMatchingTargetFinder.findTargetsOf(quizSetId)
        val candidateRowCount = matchCandidateRepository.deleteByQuizSetId(quizSetId)
        val matchingNotificationCount = matchingRecordEraser.erase(targets)
        val matchResultNotificationCount = quizSetDeleter.deleteMatchResultNotifications(quizSetId)
        return MatchingEraseSummary(
            candidateRowCount = candidateRowCount,
            personalMatchCount = targets.personalMatchIds.size,
            groupMatchCount = targets.groupMatchIds.size,
            rematchCount = targets.rematchIds.size,
            roomCount = targets.roomIds.size,
            notificationCount = matchingNotificationCount + matchResultNotificationCount,
        )
    }

    private fun validateQaToolsEnabled() {
        if (!adminQaToolsProperties.enabled) {
            throw WarnException(ErrorCode.FORBIDDEN, "QA 도구가 꺼져 있습니다. QA 도구 스위치를 켠 환경에서만 쓸 수 있습니다.")
        }
    }

    private fun validateQuizSetExists(quizSetId: Long) {
        if (!quizSetRepository.existsById(quizSetId)) throw WarnException(ErrorCode.NOT_FOUND)
    }
}

class MatchingEraseSummary(
    val candidateRowCount: Int,
    val personalMatchCount: Int,
    val groupMatchCount: Int,
    val rematchCount: Int,
    val roomCount: Int,
    val notificationCount: Int,
) {
    fun toDisplayText(): String =
        "1:1 후보 ${candidateRowCount}행, 1:1 신청 ${personalMatchCount}건, 그룹 ${groupMatchCount}개, " +
            "재매칭 ${rematchCount}건, 채팅방 ${roomCount}개, 알림 ${notificationCount}개"
}
