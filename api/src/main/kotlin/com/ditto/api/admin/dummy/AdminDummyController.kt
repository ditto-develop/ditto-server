package com.ditto.api.admin.dummy

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.dummy.dto.DummyGenerateForm
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 더미 참여자 생성(서버 렌더링). 선택한 퀴즈셋을 랜덤하게 푼 더미 회원을 남/여 인원수만큼 만든다.
 * 데이터(회원·진행·답변)만 생성하며, 매칭 후보는 '매칭 실행'에서 별도로 재생성한다.
 */
@Controller
class AdminDummyController(
    private val adminDummyService: AdminDummyService,
    private val quizSetRepository: QuizSetRepository,
) {
    @GetMapping("/admin/dummy")
    fun page(model: Model): String {
        model.addAttribute("form", DummyGenerateForm())
        model.addAttribute("quizSets", quizSetRepository.findAllByOrderByWeekStartedOnDescIdDesc())
        model.addAttribute("dummyCount", adminDummyService.countDummies())
        model.addAttribute("active", "dummy")
        return "dummy"
    }

    @PostMapping("/admin/dummy")
    fun generate(
        @ModelAttribute("form") form: DummyGenerateForm,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        runCatching { adminDummyService.generate(form) }
            .onSuccess { created ->
                log.info { "어드민[${admin.displayName}] 이 퀴즈셋 #${form.quizSetId} 에 더미 ${created}명 생성" }
                redirectAttributes.addFlashAttribute("message", "퀴즈셋 #${form.quizSetId} 에 더미 ${created}명을 생성했습니다.")
            }
            .onFailure { e ->
                // 입력값 오류(WarnException)는 화면에 안내하고, 예기치 못한 예외는 전역 핸들러로 전파한다.
                if (e !is WarnException) throw e
                redirectAttributes.addFlashAttribute("error", e.message)
            }
        return "redirect:/admin/dummy"
    }

    @PostMapping("/admin/dummy/clear")
    fun clear(
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        val deleted = adminDummyService.deleteAllDummies()
        log.info { "어드민[${admin.displayName}] 이 더미 ${deleted}명 삭제" }
        redirectAttributes.addFlashAttribute("message", "더미 ${deleted}명을 삭제했습니다.")
        return "redirect:/admin/dummy"
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
