package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.QuizSetForm
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 퀴즈셋 주차별 CRUD + 하위 Quiz/QuizChoice 관리(서버 렌더링).
 * 하위 항목 라우트는 quizSetId 를 경로에 포함해 작업 후 상세로 복귀한다.
 */
@Controller
@RequestMapping("/admin/quiz-sets")
class AdminQuizController(
    private val adminQuizService: AdminQuizService,
) {
    @GetMapping
    fun list(model: Model): String {
        model.addAttribute("quizSets", adminQuizService.listQuizSets())
        model.addAttribute("active", "quiz")
        return "quiz/list"
    }

    @GetMapping("/new")
    fun newForm(model: Model): String {
        model.addAttribute("form", QuizSetForm())
        model.addAttribute("mode", "create")
        model.addAttribute("active", "quiz")
        return "quiz/form"
    }

    @PostMapping
    fun create(@ModelAttribute("form") form: QuizSetForm, redirectAttributes: RedirectAttributes): String {
        val created = adminQuizService.createQuizSet(form)
        redirectAttributes.addFlashAttribute("message", "퀴즈셋이 생성되었습니다.")
        return "redirect:/admin/quiz-sets/${created.id}"
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        val quizSet = adminQuizService.getQuizSet(id)
        val quizzes = adminQuizService.getQuizzes(id)
        model.addAttribute("quizSet", quizSet)
        model.addAttribute("quizzes", quizzes)
        model.addAttribute("choicesByQuiz", quizzes.associate { it.id to adminQuizService.getChoices(it.id) })
        model.addAttribute("active", "quiz")
        return "quiz/detail"
    }

    @GetMapping("/{id}/edit")
    fun editForm(@PathVariable id: Long, model: Model): String {
        model.addAttribute("form", QuizSetForm.from(adminQuizService.getQuizSet(id)))
        model.addAttribute("mode", "edit")
        model.addAttribute("quizSetId", id)
        model.addAttribute("active", "quiz")
        return "quiz/form"
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @ModelAttribute("form") form: QuizSetForm,
        redirectAttributes: RedirectAttributes,
    ): String {
        adminQuizService.updateQuizSet(id, form)
        redirectAttributes.addFlashAttribute("message", "퀴즈셋이 수정되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }

    @PostMapping("/{id}/activate")
    fun activate(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String {
        adminQuizService.activate(id)
        redirectAttributes.addFlashAttribute("message", "활성화되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String {
        adminQuizService.deactivate(id)
        redirectAttributes.addFlashAttribute("message", "비활성화되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }

    @PostMapping("/{id}/delete")
    fun delete(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String {
        adminQuizService.deleteQuizSet(id)
        redirectAttributes.addFlashAttribute("message", "퀴즈셋이 삭제되었습니다.")
        return "redirect:/admin/quiz-sets"
    }

    @PostMapping("/{id}/quizzes")
    fun addQuiz(
        @PathVariable id: Long,
        @RequestParam question: String,
        @RequestParam displayOrder: Int,
        redirectAttributes: RedirectAttributes,
    ): String {
        adminQuizService.addQuiz(id, question, displayOrder)
        redirectAttributes.addFlashAttribute("message", "퀴즈가 추가되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }

    @PostMapping("/{id}/quizzes/{quizId}/update")
    fun updateQuiz(
        @PathVariable id: Long,
        @PathVariable quizId: Long,
        @RequestParam question: String,
        @RequestParam displayOrder: Int,
        redirectAttributes: RedirectAttributes,
    ): String {
        adminQuizService.updateQuiz(quizId, question, displayOrder)
        redirectAttributes.addFlashAttribute("message", "퀴즈가 수정되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }

    @PostMapping("/{id}/quizzes/{quizId}/delete")
    fun deleteQuiz(
        @PathVariable id: Long,
        @PathVariable quizId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        adminQuizService.deleteQuiz(quizId)
        redirectAttributes.addFlashAttribute("message", "퀴즈가 삭제되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }

    @PostMapping("/{id}/quizzes/{quizId}/choices")
    fun addChoice(
        @PathVariable id: Long,
        @PathVariable quizId: Long,
        @RequestParam content: String,
        @RequestParam displayOrder: Int,
        redirectAttributes: RedirectAttributes,
    ): String {
        adminQuizService.addChoice(quizId, content, displayOrder)
        redirectAttributes.addFlashAttribute("message", "선택지가 추가되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }

    @PostMapping("/{id}/quizzes/{quizId}/choices/{choiceId}/update")
    fun updateChoice(
        @PathVariable id: Long,
        @PathVariable choiceId: Long,
        @RequestParam content: String,
        @RequestParam displayOrder: Int,
        redirectAttributes: RedirectAttributes,
    ): String {
        adminQuizService.updateChoice(choiceId, content, displayOrder)
        redirectAttributes.addFlashAttribute("message", "선택지가 수정되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }

    @PostMapping("/{id}/quizzes/{quizId}/choices/{choiceId}/delete")
    fun deleteChoice(
        @PathVariable id: Long,
        @PathVariable choiceId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        adminQuizService.deleteChoice(choiceId)
        redirectAttributes.addFlashAttribute("message", "선택지가 삭제되었습니다.")
        return "redirect:/admin/quiz-sets/$id"
    }
}
