package com.ditto.api.admin.match

import com.ditto.api.match.service.MatchmakingService
import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 매칭 운영. 실시간 스케줄러(목 05:00)와 별개로, 어드민이 동일 배치 로직을 수동 실행하거나
 * 특정 퀴즈셋의 매칭 후보를 재생성한다. 수동 실행은 어드민 설정 시각([ServerTimeProvider]) 기준으로 동작한다.
 */
@Controller
@RequestMapping("/admin/matching")
class AdminMatchController(
    private val matchmakingService: MatchmakingService,
    private val serverTimeProvider: ServerTimeProvider,
    private val quizSetRepository: QuizSetRepository,
) {
    @GetMapping
    fun page(model: Model): String {
        model.addAttribute("quizSets", quizSetRepository.findAllByOrderByYearDescMonthDescWeekDescIdDesc())
        model.addAttribute("currentTime", serverTimeProvider.now())
        model.addAttribute("active", "matching")
        return "matching"
    }

    @PostMapping("/run-scheduled")
    fun runScheduled(redirectAttributes: RedirectAttributes): String {
        matchmakingService.runScheduledMatching(serverTimeProvider.now())
        redirectAttributes.addFlashAttribute("message", "마감된 퀴즈셋의 매칭 배치를 실행했습니다.")
        return "redirect:/admin/matching"
    }

    @PostMapping("/quiz-sets/{id}/regenerate")
    fun regenerate(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String {
        matchmakingService.generateMatchingCandidates(id)
        redirectAttributes.addFlashAttribute("message", "퀴즈셋 #$id 의 매칭 후보를 재생성했습니다.")
        return "redirect:/admin/matching"
    }
}
