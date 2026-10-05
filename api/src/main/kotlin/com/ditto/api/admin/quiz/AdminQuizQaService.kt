package com.ditto.api.admin.quiz

import com.ditto.api.admin.cleanup.ChatRoomEraser
import com.ditto.api.admin.cleanup.MatchingRecordEraser
import com.ditto.api.admin.cleanup.MatchingRecordTargets
import com.ditto.api.config.AdminQaToolsProperties
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.rematch.repository.RematchRepository
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
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val rematchRepository: RematchRepository,
    private val chatRoomEraser: ChatRoomEraser,
    private val matchingRecordEraser: MatchingRecordEraser,
    private val quizSetDeleter: QuizSetDeleter,
) {
    /** 퀴즈셋·답·진행은 남기고 매칭 기록만 지운다. 같은 셋으로 매칭을 다시 돌릴 수 있다. */
    fun resetMatching(quizSetId: Long): MatchingResetSummary {
        validateQaToolsEnabled()
        validateQuizSetExists(quizSetId)
        return eraseMatchingOf(quizSetId)
    }

    /** 매칭 기록을 먼저 지워 일반 삭제가 막히지 않게 한 뒤 퀴즈셋까지 지운다. */
    fun forceDelete(quizSetId: Long): MatchingResetSummary {
        validateQaToolsEnabled()
        validateQuizSetExists(quizSetId)
        val summary = eraseMatchingOf(quizSetId)
        quizSetDeleter.delete(quizSetId)
        return summary
    }

    private fun eraseMatchingOf(quizSetId: Long): MatchingResetSummary {
        val targets = findMatchingTargetsOf(quizSetId)
        val candidateRowCount = matchCandidateRepository.deleteByQuizSetId(quizSetId)
        val notificationCount =
            matchingRecordEraser.erase(targets) + quizSetDeleter.deleteMatchResultNotifications(quizSetId)
        return MatchingResetSummary(
            candidateRowCount = candidateRowCount,
            personalMatchCount = targets.personalMatchIds.size,
            groupMatchCount = targets.groupMatchIds.size,
            rematchCount = targets.rematchIds.size,
            roomCount = targets.roomIds.size,
            notificationCount = notificationCount,
        )
    }

    // 재매칭은 그룹에서만 나오므로 이 셋의 그룹으로 찾는다.
    private fun findMatchingTargetsOf(quizSetId: Long): MatchingRecordTargets {
        val personalMatchIds = personalMatchRepository.findByQuizSetIdIn(listOf(quizSetId)).map { it.id }.toSet()
        val groupMatchIds = groupMatchRepository.findByQuizSetId(quizSetId).map { it.id }.toSet()
        val rematchIds = findRematchIdsFrom(groupMatchIds)
        val roomIds = chatRoomEraser.findRoomIdsFrom(ChatRoomType.PERSONAL, personalMatchIds) +
            chatRoomEraser.findRoomIdsFrom(ChatRoomType.GROUP, groupMatchIds) +
            chatRoomEraser.findRoomIdsFrom(ChatRoomType.REMATCH, rematchIds)
        return MatchingRecordTargets(roomIds, personalMatchIds, groupMatchIds, rematchIds)
    }

    private fun findRematchIdsFrom(groupMatchIds: Set<Long>): Set<Long> {
        if (groupMatchIds.isEmpty()) return emptySet()
        return rematchRepository.findAllBySourceGroupMatchIdIn(groupMatchIds).map { it.id }.toSet()
    }

    private fun validateQaToolsEnabled() {
        if (!adminQaToolsProperties.enabled) {
            throw WarnException(ErrorCode.FORBIDDEN, "QA 도구가 꺼져 있습니다. ADMIN_QA_TOOLS_ENABLED 를 켠 환경에서만 쓸 수 있습니다.")
        }
    }

    private fun validateQuizSetExists(quizSetId: Long) {
        if (!quizSetRepository.existsById(quizSetId)) throw WarnException(ErrorCode.NOT_FOUND)
    }
}

class MatchingResetSummary(
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
