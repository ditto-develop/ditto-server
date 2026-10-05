package com.ditto.api.admin.match

import com.ditto.api.match.service.MatchingBatchFacade
import com.ditto.api.match.service.MatchmakingService
import com.ditto.api.notification.notifier.MatchResultNotifier
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 매칭 운영. 실시간 스케줄러(목 05:00)와 별개로, 어드민이 동일 배치 로직을 수동 실행하거나
 * 특정 퀴즈셋의 매칭 후보를 재생성한다. 수동 실행은 어드민 설정 시각([ServerTimeProvider]) 기준으로 동작한다.
 */
@Controller
class AdminMatchController(
    private val matchmakingService: MatchmakingService,
    private val matchingBatchFacade: MatchingBatchFacade,
    private val serverTimeProvider: ServerTimeProvider,
    private val quizSetRepository: QuizSetRepository,
    private val matchResultNotifier: MatchResultNotifier,
) {
    @GetMapping("/admin/matching")
    fun page(model: Model): String {
        model.addAttribute("quizSets", quizSetRepository.findAllByOrderByWeekStartedOnDescIdDesc())
        model.addAttribute("currentTime", serverTimeProvider.now())
        model.addAttribute("active", "matching")
        return "matching"
    }

    @PostMapping("/admin/matching/run-scheduled")
    fun runScheduled(redirectAttributes: RedirectAttributes): String {
        val quizSetIds = matchingBatchFacade.runScheduledMatching(serverTimeProvider.now())
        matchResultNotifier.notifyFor(quizSetIds)
        redirectAttributes.addFlashAttribute("message", "자동 매칭을 실행했습니다.")
        return "redirect:/admin/matching"
    }

    /**
     * 후보 재생성. 성공하면 알림도 함께 남긴다 — 이번에 처음 후보를 받은 회원에게는 알려야 한다.
     * 이미 알린 회원에게 다시 가지 않는 것은 알림 쪽 중복 정책이 보장한다(퀴즈셋당 한 번).
     * 대체할 수 없어 거부되면(응답이 시작된 그룹 퀴즈셋) 실패로 보여주고 알림도 남기지 않는다.
     * 결과 요약은 저장하지 않고 이번 화면에만 한 번 보여준다.
     */
    @PostMapping("/admin/matching/quiz-sets/{id}/regenerate")
    fun regenerate(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String {
        runCatching { matchmakingService.generateMatchingCandidates(id) }
            .fold(
                onSuccess = { summary ->
                    matchResultNotifier.notifyFor(listOf(id))
                    redirectAttributes.addFlashAttribute(
                        "message",
                        "퀴즈셋 #$id 매칭 후보를 재생성했습니다.",
                    )
                    redirectAttributes.addFlashAttribute("regeneration", summary)
                },
                onFailure = { exception ->
                    if (exception !is WarnException) throw exception
                    redirectAttributes.addFlashAttribute("error", regenerationFailedMessage(id, exception))
                },
            )
        return "redirect:/admin/matching"
    }

    private fun regenerationFailedMessage(quizSetId: Long, exception: WarnException): String {
        val failed = "퀴즈셋 #$quizSetId 매칭 재생성 실패: ${exception.message}"
        if (exception.errorCode != ErrorCode.MATCH_CANDIDATES_ALREADY_RESPONDED) return failed
        return "$failed. 퀴즈셋 상세의 [매칭 기록 초기화] 뒤 다시 재생성하세요."
    }
}
