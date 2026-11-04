package com.ditto.api.quiz.stat

/**
 * 퀴즈셋 통계의 순수 계산. 입력은 완주자들의 답이고 DB·시각을 모른다.
 *
 * 규칙은 `docs/domains/quiz.md` "퀴즈 기록·통계"에 있다. 요약:
 * - 비율은 정수 %, 문항 안 합이 100이 되도록 최대 잉여법으로 보정한다.
 * - 소수 의견 = `선택 수 × 5 ≤ 참여자 수` 이면서 참여자 [MINORITY_MIN_PARTICIPANTS]명 이상.
 * - 겹친 사람 = 본인 제외, 같은 답이 `ceil(문항 수 × 2/3)`개 이상인 완주자 수.
 */
object QuizStatCalculator {

    const val MINORITY_MIN_PARTICIPANTS = 20
    private const val MINORITY_RATIO_DIVISOR = 5

    /**
     * @param choiceIdsByQuizId 문항별 선택지 ID (표시 순서)
     * @param answersByMemberId 완주자별 `quizId → choiceId`
     */
    fun calculate(
        choiceIdsByQuizId: Map<Long, List<Long>>,
        answersByMemberId: Map<Long, Map<Long, Long>>,
    ): QuizStatResult {
        val participantCount = answersByMemberId.size
        val countsByChoiceId = answersByMemberId.values
            .flatMap { it.values }
            .groupingBy { it }
            .eachCount()

        val choices = choiceIdsByQuizId.flatMap { (quizId, choiceIds) ->
            val counts = choiceIds.map { countsByChoiceId[it] ?: 0 }
            val percents = percentsSummingTo100(counts)
            choiceIds.mapIndexed { i, choiceId ->
                ChoiceStat(
                    quizId = quizId,
                    choiceId = choiceId,
                    selectCount = counts[i],
                    percent = percents[i],
                    minority = isMinority(counts[i], participantCount),
                )
            }
        }
        val minorityChoiceIds = choices.filter { it.minority }.map { it.choiceId }.toSet()

        val threshold = overlapThreshold(choiceIdsByQuizId.size)
        val members = answersByMemberId.map { (memberId, answers) ->
            MemberStat(
                memberId = memberId,
                minorityCount = answers.values.count { it in minorityChoiceIds },
                overlapCount = answersByMemberId.count { (otherId, otherAnswers) ->
                    otherId != memberId && sameAnswerCount(answers, otherAnswers) >= threshold
                },
            )
        }

        return QuizStatResult(
            participantCount = participantCount,
            overlapThreshold = threshold,
            choices = choices,
            members = members,
        )
    }

    /** 반올림한 %가 아니라 실제 선택 수로 판정한다 — 반올림하면 19.6%와 20.4%가 같은 20이 된다. */
    fun isMinority(selectCount: Int, participantCount: Int): Boolean =
        participantCount >= MINORITY_MIN_PARTICIPANTS && selectCount * MINORITY_RATIO_DIVISOR <= participantCount

    /** 12문항 8개, 10문항 7개, 15문항 10개. */
    fun overlapThreshold(quizCount: Int): Int = (quizCount * 2 + 2) / 3

    /**
     * 최대 잉여법: 내림한 %를 먼저 주고, 모자란 만큼 소수부가 큰 순으로 1씩 더한다(동률은 앞 선택지부터).
     * 아무도 고르지 않은 문항은 전부 0이다.
     */
    fun percentsSummingTo100(counts: List<Int>): List<Int> {
        val total = counts.sum()
        if (total == 0) return counts.map { 0 }

        val floors = counts.map { it * 100 / total }
        val remainders = counts.map { it * 100 % total }
        val missing = 100 - floors.sum()
        val bumped = remainders.indices
            .sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
            .take(missing)
            .toSet()
        return floors.mapIndexed { i, floor -> if (i in bumped) floor + 1 else floor }
    }

    private fun sameAnswerCount(a: Map<Long, Long>, b: Map<Long, Long>): Int =
        a.count { (quizId, choiceId) -> b[quizId] == choiceId }
}

data class QuizStatResult(
    val participantCount: Int,
    val overlapThreshold: Int,
    val choices: List<ChoiceStat>,
    val members: List<MemberStat>,
)

data class ChoiceStat(
    val quizId: Long,
    val choiceId: Long,
    val selectCount: Int,
    val percent: Int,
    val minority: Boolean,
)

data class MemberStat(
    val memberId: Long,
    val minorityCount: Int,
    val overlapCount: Int,
)
