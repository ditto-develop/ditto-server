package com.ditto.api.quiz.service

import com.ditto.api.quiz.dto.QuizHistoryAnswer
import com.ditto.api.quiz.dto.QuizHistoryChoice
import com.ditto.api.quiz.dto.QuizHistoryCurrentWeek
import com.ditto.api.quiz.dto.QuizHistoryDetailResponse
import com.ditto.api.quiz.dto.QuizHistoryPhase
import com.ditto.api.quiz.dto.QuizHistoryResponse
import com.ditto.api.quiz.dto.QuizHistoryResult
import com.ditto.api.quiz.dto.QuizHistorySummary
import com.ditto.api.quiz.dto.QuizHistoryWeek
import com.ditto.api.quiz.stat.QuizAnswerSheet
import com.ditto.api.quiz.stat.QuizAnswerSheetLoader
import com.ditto.api.quiz.stat.QuizResultSchedule
import com.ditto.api.quiz.stat.QuizStatSnapshot
import com.ditto.api.quiz.stat.QuizStatSnapshotService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizMemberStatRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 퀴즈 기록 탭(IMP-1 BE-2). "참여" = 그 셋을 끝까지 제출(COMPLETED)한 것이다 — 중간에 멈춘 셋은 기록에 없다.
 *
 * 마감 여부는 서버 시각으로 판단하고([QuizResultSchedule]), 마감된 셋의 숫자는 스냅샷에서 읽는다(없으면 지연 생성).
 * 응답 마감 뒤에 완주한 회원은 스냅샷에 자기 행이 없어 자기 답을 스냅샷에 대조해 센다.
 * 트랜잭션을 열지 않는다 — 지연 생성이 자기 트랜잭션에서 커밋돼야 같은 요청이 그 결과를 읽는다.
 */
@Service
class QuizHistoryService(
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizMemberStatRepository: QuizMemberStatRepository,
    private val quizStatSnapshotService: QuizStatSnapshotService,
    private val quizAnswerSheetLoader: QuizAnswerSheetLoader,
) {

    fun getHistory(memberId: Long, now: LocalDateTime): QuizHistoryResponse {
        val quizSets = quizSetRepository.findCompletedQuizSetsOf(memberId)
        val progresses = quizProgressRepository.findByMemberIdAndQuizSetIdIn(memberId, quizSets.map { it.id })
        val closedIds = quizSets.filter { QuizResultSchedule.isOpen(it, now) }.map { it.id }
        // 배치가 놓친 셋이 있으면 여기서 메운다 — 회원 행을 읽기 전에 만들어야 숫자가 빠지지 않는다.
        closedIds.forEach { quizStatSnapshotService.ensureCreated(it) }
        val minorityBySetId = quizMemberStatRepository.findByMemberIdAndQuizSetIdIn(memberId, closedIds)
            .associate { it.quizSetId to it.minorityCount }
        val progressBySetId = progresses.associateBy { it.quizSetId }

        val weeks = quizSets.map { set ->
            val lateCompletion = progressBySetId[set.id]?.isCompletedLate(set.endDate) ?: false
            QuizHistoryWeek(
                quizSetId = set.id,
                weekStartedOn = set.weekStartedOn,
                matchingType = set.matchingType,
                title = set.title,
                lateCompletion = lateCompletion,
                minorityCount = when {
                    set.id !in closedIds -> null
                    lateCompletion -> minorityCountOfLateCompleter(memberId, set, now)
                    else -> minorityBySetId[set.id] ?: 0
                },
            )
        }
        val thisWeek = OperationWeek.containing(now.toLocalDate()).startedOn
        val currentWeek = quizSets.firstOrNull { it.weekStartedOn == thisWeek }?.let {
            QuizHistoryCurrentWeek(
                quizSetId = it.id,
                resultOpen = QuizResultSchedule.isOpen(it, now),
                resultOpensAt = QuizResultSchedule.opensAt(it),
            )
        }

        return QuizHistoryResponse(
            summary = QuizHistorySummary(
                participatedWeeks = quizSets.map { it.weekStartedOn }.distinct().size,
                answeredQuestions = progresses.sumOf { it.totalCount },
                minorityCount = weeks.sumOf { it.minorityCount ?: 0 },
            ),
            currentWeek = currentWeek,
            weeks = weeks,
        )
    }

    fun getDetail(memberId: Long, quizSetId: Long, now: LocalDateTime): QuizHistoryDetailResponse {
        val progress = quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSetId)
        if (progress == null || progress.status != QuizProgressStatus.COMPLETED) {
            throw WarnException(ErrorCode.QUIZ_HISTORY_NOT_FOUND)
        }
        val quizSet = quizSetRepository.findById(quizSetId).orElseThrow { WarnException(ErrorCode.QUIZ_HISTORY_NOT_FOUND) }
        val sheet = quizAnswerSheetLoader.load(quizSetId, listOf(memberId))
        val lateCompletion = progress.isCompletedLate(quizSet.endDate)
        val snapshot = quizStatSnapshotService.findIfClosed(quizSet, now)

        return QuizHistoryDetailResponse(
            quizSetId = quizSet.id,
            title = quizSet.title,
            matchingType = quizSet.matchingType,
            weekStartedOn = quizSet.weekStartedOn,
            phase = if (snapshot != null) QuizHistoryPhase.CLOSED else QuizHistoryPhase.IN_PROGRESS,
            resultOpensAt = QuizResultSchedule.opensAt(quizSet),
            participantCount = snapshot?.participantCount?.toLong() ?: liveParticipantCount(quizSet, now),
            lateCompletion = lateCompletion,
            answers = answersOf(memberId, sheet, snapshot),
            result = snapshot?.let {
                if (lateCompletion) lateCompleterResult(memberId, sheet, it) else onTimeResult(memberId, quizSetId, it)
            },
        )
    }

    // 스냅샷에 자기 행이 없다. 겹친 사람 수는 다른 참여자 전원과 비교해야 해서 세지 않는다.
    private fun lateCompleterResult(memberId: Long, sheet: QuizAnswerSheet, snapshot: QuizStatSnapshot) =
        QuizHistoryResult(
            overlapThreshold = snapshot.overlapThreshold,
            overlapCount = 0,
            minorityCount = sheet.minorityCountOf(memberId, snapshot),
        )

    private fun onTimeResult(memberId: Long, quizSetId: Long, snapshot: QuizStatSnapshot): QuizHistoryResult {
        val memberStat = quizMemberStatRepository.findByQuizSetIdAndMemberId(quizSetId, memberId)
        return QuizHistoryResult(
            overlapThreshold = snapshot.overlapThreshold,
            overlapCount = memberStat?.overlapCount ?: 0,
            minorityCount = memberStat?.minorityCount ?: 0,
        )
    }

    private fun minorityCountOfLateCompleter(memberId: Long, quizSet: QuizSet, now: LocalDateTime): Int {
        val snapshot = quizStatSnapshotService.findIfClosed(quizSet, now) ?: return 0
        return quizAnswerSheetLoader.load(quizSet.id, listOf(memberId)).minorityCountOf(memberId, snapshot)
    }

    private fun answersOf(memberId: Long, sheet: QuizAnswerSheet, snapshot: QuizStatSnapshot?): List<QuizHistoryAnswer> =
        sheet.quizzes.map { quiz ->
            QuizHistoryAnswer(
                quizId = quiz.id,
                order = quiz.displayOrder,
                question = quiz.question,
                choices = sheet.choicesOf(quiz.id).map { choice ->
                    QuizHistoryChoice(
                        choiceId = choice.id,
                        content = choice.content,
                        percent = snapshot?.percentOf(choice.id),
                        minority = snapshot?.isMinority(choice.id),
                    )
                },
                myChoiceId = sheet.choiceOf(memberId, quiz.id),
            )
        }

    /**
     * 진행 중 참여자 수는 홈(`GET /quiz-progress/current`)과 같은 값이어야 한다 — 이번 주 활성 셋 전체의 완주자 수다.
     * 이번 주 활성 셋이 아니면(지난 셋을 비활성화해 결과가 아직인 경우 등) 그 셋만 센다.
     */
    private fun liveParticipantCount(quizSet: QuizSet, now: LocalDateTime): Long {
        val currentIds = quizSetRepository.findCurrentWeekActive(now).map { it.id }
        val ids = if (quizSet.id in currentIds) currentIds else listOf(quizSet.id)
        return quizProgressRepository.countByQuizSetIdInAndStatus(ids, QuizProgressStatus.COMPLETED)
    }
}
