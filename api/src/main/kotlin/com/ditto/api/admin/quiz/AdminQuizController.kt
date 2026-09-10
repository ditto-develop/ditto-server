package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.QuizSetForm
import com.ditto.common.exception.WarnException
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 퀴즈셋 주차별 CRUD(서버 렌더링). 문항·선택지는 퀴즈셋 폼에 함께 실려 저장 한 번으로 반영된다.
 */
@Controller
class AdminQuizController(
    private val adminQuizService: AdminQuizService,
) {
    @GetMapping("/admin/quiz-sets")
    fun list(model: Model): String {
        model.addAttribute("quizSets", adminQuizService.listQuizSets())
        model.addAttribute("active", "quiz")
        return "quiz/list"
    }

    @GetMapping("/admin/quiz-sets/new")
    fun newPage(model: Model): String {
        model.addAttribute("form", QuizSetForm.blank(NEW_QUIZ_ROW_COUNT))
        model.addAttribute("mode", "create")
        model.addAttribute("answerCounts", emptyMap<Long, Long>())
        model.addAttribute("active", "quiz")
        return "quiz/form"
    }

    /**
     * 저장이 거부되면 리다이렉트하지 않고 폼을 그대로 다시 그린다.
     * 문항 열 몇 개를 채운 뒤 한 칸 실수로 전부 다시 입력하게 만들지 않으려는 것이다.
     */
    @PostMapping("/admin/quiz-sets")
    fun create(
        @ModelAttribute("form") form: QuizSetForm,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String = runCatching { adminQuizService.createQuizSet(form) }
        .fold(
            onSuccess = { created ->
                redirectAttributes.addFlashAttribute("message", "퀴즈셋이 생성되었습니다.")
                "redirect:/admin/quiz-sets/${created.id}"
            },
            onFailure = { exception ->
                if (exception !is WarnException) throw exception
                model.addAttribute("error", exception.message)
                model.addAttribute("mode", "create")
                model.addAttribute("answerCounts", emptyMap<Long, Long>())
                model.addAttribute("active", "quiz")
                "quiz/form"
            },
        )

    @GetMapping("/admin/quiz-sets/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        val quizzes = adminQuizService.getQuizzes(id)
        model.addAttribute("quizSet", adminQuizService.getQuizSet(id))
        model.addAttribute("quizzes", quizzes)
        model.addAttribute("choicesByQuiz", adminQuizService.getChoicesByQuizIds(quizzes.map { it.id }))
        model.addAttribute("active", "quiz")
        return "quiz/detail"
    }

    @GetMapping("/admin/quiz-sets/{id}/edit")
    fun editPage(@PathVariable id: Long, model: Model): String {
        val quizzes = adminQuizService.getQuizzes(id)
        val quizIds = quizzes.map { it.id }
        val form = QuizSetForm.from(
            quizSet = adminQuizService.getQuizSet(id),
            quizzes = quizzes,
            choicesByQuiz = adminQuizService.getChoicesByQuizIds(quizIds),
        )
        model.addAttribute("form", form)
        model.addAttribute("mode", "edit")
        model.addAttribute("quizSetId", id)
        model.addAttribute("answerCounts", adminQuizService.getAnswerCountsByQuizIds(quizIds))
        model.addAttribute("active", "quiz")
        return "quiz/form"
    }

    @PostMapping("/admin/quiz-sets/{id}")
    fun update(
        @PathVariable id: Long,
        @ModelAttribute("form") form: QuizSetForm,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String = runCatching { adminQuizService.updateQuizSet(id, form) }
        .fold(
            onSuccess = {
                redirectAttributes.addFlashAttribute("message", "퀴즈셋이 수정되었습니다.")
                "redirect:/admin/quiz-sets/$id"
            },
            onFailure = { exception ->
                if (exception !is WarnException) throw exception
                model.addAttribute("error", exception.message)
                model.addAttribute("mode", "edit")
                model.addAttribute("quizSetId", id)
                model.addAttribute("answerCounts", adminQuizService.getAnswerCountsByQuizIds(form.quizzes.mapNotNull { it.id }))
                model.addAttribute("active", "quiz")
                "quiz/form"
            },
        )

    @PostMapping("/admin/quiz-sets/{id}/activate")
    fun activate(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String {
        adminQuizService.activate(id)
        redirectAttributes.addFlashAttribute("message", "활성화되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }

    @PostMapping("/admin/quiz-sets/{id}/deactivate")
    fun deactivate(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String {
        adminQuizService.deactivate(id)
        redirectAttributes.addFlashAttribute("message", "비활성화되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }

    @PostMapping("/admin/quiz-sets/{id}/delete")
    fun delete(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String {
        adminQuizService.deleteQuizSet(id)
        redirectAttributes.addFlashAttribute("message", "퀴즈셋이 삭제되었습니다.")
        return "redirect:/admin/quiz-sets"
    }

    companion object {
        private const val NEW_QUIZ_ROW_COUNT = 3
    }
}
