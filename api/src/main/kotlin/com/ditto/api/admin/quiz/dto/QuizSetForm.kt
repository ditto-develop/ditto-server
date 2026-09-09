package com.ditto.api.admin.quiz.dto

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizSet
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

/**
 * 퀴즈셋 생성·수정 폼 바인딩(스프링 MVC 폼 백킹 빈이라 주생성자는 public 으로 둔다 — 바인딩 시 스프링이 인스턴스화).
 * 일시 형식은 datetime-local 인풋과 맞춰야 한다 — 형식 지정이 없으면 수정 폼 렌더링 시
 * 로캘 형식으로 출력돼 브라우저가 값을 버리고 빈 칸으로 표시된다(#93).
 */
class QuizSetForm(
    var category: String = "",
    var title: String = "",
    var description: String? = null,
    @field:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    var startDate: LocalDateTime? = null,
    @field:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    var endDate: LocalDateTime? = null,
    var matchingType: MatchingType = MatchingType.ONE_TO_ONE,
    var isActive: Boolean = false,
    // 비어 있으면 문항을 건드리지 않는다. 전체 삭제는 퀴즈셋 삭제로만 한다.
    var quizzes: MutableList<QuizForm> = mutableListOf(),
) {
    fun requiredStartDate(): LocalDateTime =
        startDate ?: throw WarnException(ErrorCode.BAD_REQUEST, "시작일시는 필수입니다.")

    fun requiredEndDate(): LocalDateTime =
        endDate ?: throw WarnException(ErrorCode.BAD_REQUEST, "종료일시는 필수입니다.")

    companion object {
        fun from(
            quizSet: QuizSet,
            quizzes: List<Quiz>,
            choicesByQuiz: Map<Long, List<QuizChoice>>,
        ) = QuizSetForm(
            category = quizSet.category,
            title = quizSet.title,
            description = quizSet.description,
            startDate = quizSet.startDate,
            endDate = quizSet.endDate,
            matchingType = quizSet.matchingType,
            isActive = quizSet.isActive,
            quizzes = quizzes.map { quiz ->
                QuizForm(
                    id = quiz.id,
                    question = quiz.question,
                    choices = choicesByQuiz[quiz.id].orEmpty()
                        .map { QuizChoiceForm(id = it.id, content = it.content) }
                        .toMutableList(),
                )
            }.toMutableList(),
        )

        fun blank(quizRowCount: Int) = QuizSetForm(
            quizzes = MutableList(quizRowCount) { QuizForm.blank() },
        )
    }
}
