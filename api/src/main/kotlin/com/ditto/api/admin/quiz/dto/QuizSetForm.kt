package com.ditto.api.admin.quiz.dto

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.system.OperationWeek
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

/**
 * 퀴즈셋 생성·수정 폼 바인딩(스프링 MVC 폼 백킹 빈이라 주생성자는 public 으로 둔다 — 바인딩 시 스프링이 인스턴스화).
 * 기간은 직접 받지 않는다. 주차(그 주 월요일)만 고르게 하고 응답 기간은 서버가 월~수로 고정한다.
 * 날짜 형식을 지정하지 않으면 수정 폼 렌더링 시 로캘 형식으로 출력돼 select 값과 어긋난다(#93과 같은 문제).
 */
class QuizSetForm(
    var category: String = "",
    var title: String = "",
    var description: String? = null,
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var weekStartedOn: LocalDate? = null,
    var matchingType: MatchingType = MatchingType.ONE_TO_ONE,
    var isActive: Boolean = false,
    // 비어 있으면 문항을 건드리지 않는다. 전체 삭제는 퀴즈셋 삭제로만 한다.
    var quizzes: MutableList<QuizForm> = mutableListOf(),
) {
    /** select 값이 조작돼 월요일이 아닌 날짜가 오면 [OperationWeek]의 검사에 걸린다 — 요청 잘못으로 바꿔 던진다. */
    fun requiredWeek(): OperationWeek {
        val monday = weekStartedOn ?: throw WarnException(ErrorCode.BAD_REQUEST, "주차는 필수입니다.")
        return runCatching { OperationWeek(monday) }
            .getOrElse { throw WarnException(ErrorCode.BAD_REQUEST, "주차는 월요일 날짜여야 합니다: $monday") }
    }

    /** 제출된 문항은 모두 채워져 있어야 한다. 번호는 화면에 보이는 순서 그대로다. */
    fun validatedQuizzes(): List<QuizForm> {
        quizzes.forEachIndexed { index, quizForm ->
            val blankField = quizForm.blankFieldName() ?: return@forEachIndexed
            throw WarnException(ErrorCode.BAD_REQUEST, "${index + 1}번 문항의 $blankField 항목이 비어 있습니다.")
        }

        val duplicatedQuizIds = quizzes.mapNotNull { it.id }.groupBy { it }.filterValues { it.size > 1 }.keys
        if (duplicatedQuizIds.isNotEmpty()) {
            throw WarnException(ErrorCode.BAD_REQUEST, "같은 문항이 두 번 제출됐습니다: $duplicatedQuizIds")
        }
        return quizzes
    }

    companion object {
        fun from(
            quizSet: QuizSet,
            quizzes: List<Quiz>,
            choicesByQuiz: Map<Long, List<QuizChoice>>,
        ) = QuizSetForm(
            category = quizSet.category,
            title = quizSet.title,
            description = quizSet.description,
            weekStartedOn = quizSet.weekStartedOn,
            matchingType = quizSet.matchingType,
            isActive = quizSet.isActive,
            quizzes = quizzes.map { quiz ->
                QuizForm(
                    id = quiz.id,
                    question = quiz.question,
                    // 개별 추가 UI 시절 선택지가 2개 미만인 문항이 남아 있다. 빈 칸을 채워 화면에서 고칠 수 있게 한다.
                    choices = choicesByQuiz[quiz.id].orEmpty()
                        .map { QuizChoiceForm(id = it.id, content = it.content) }
                        .padToMinimumChoices(),
                )
            }.toMutableList(),
        )

        fun blank(quizRowCount: Int) = QuizSetForm(
            quizzes = MutableList(quizRowCount) { QuizForm.blank() },
        )
    }
}

private fun List<QuizChoiceForm>.padToMinimumChoices(): MutableList<QuizChoiceForm> {
    val missingCount = (QuizForm.NEW_QUIZ_CHOICE_COUNT - size).coerceAtLeast(0)
    return (this + List(missingCount) { QuizChoiceForm() }).toMutableList()
}
