package com.ditto.api.admin.match

import com.ditto.api.config.AdminQaToolsProperties
import com.ditto.api.match.service.MatchingBatchFacade
import com.ditto.api.match.service.MatchmakingService
import com.ditto.api.match.service.SecretRoundBatchResult
import com.ditto.api.notification.notifier.MatchResultNotifier
import com.ditto.api.notification.notifier.SecretMatchResultNotifier
import com.ditto.api.notification.notifier.SecretMatchingInviteNotifier
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 매칭 운영. 실시간 스케줄러(목 00:01, 시크릿 매칭 금·토 20:01)와 별개로, 어드민이 같은 배치 로직을 수동 실행하거나
 * 특정 퀴즈셋의 매칭 후보를 다시 만든다. 수동 실행은 어드민 설정 시각([ServerTimeProvider]) 기준으로 동작한다.
 */
@Controller
class AdminMatchController(
    private val matchmakingService: MatchmakingService,
    private val matchingBatchFacade: MatchingBatchFacade,
    private val serverTimeProvider: ServerTimeProvider,
    private val quizSetRepository: QuizSetRepository,
    private val matchResultNotifier: MatchResultNotifier,
    private val secretMatchResultNotifier: SecretMatchResultNotifier,
    private val secretMatchingInviteNotifier: SecretMatchingInviteNotifier,
    private val adminQaToolsProperties: AdminQaToolsProperties,
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
        redirectAttributes.addFlashAttribute("message", "정기 매칭을 지금 돌렸습니다.")
        return "redirect:/admin/matching"
    }

    /** 지금 서버 시각에 돌 차례인 시크릿 매칭 라운드를 돌린다. 시각 조정으로 금·토 라운드를 QA 할 때 쓴다. */
    @PostMapping("/admin/matching/run-secret-round")
    fun runSecretRound(redirectAttributes: RedirectAttributes): String {
        val now = serverTimeProvider.now()
        val result = matchingBatchFacade.runSecretRound(now)
        matchResultNotifier.notifyFor(result.backfilledRegularQuizSetIds)
        secretMatchResultNotifier.notifyFor(result.roundResults)
        addSecretRoundFlash(redirectAttributes, now, result)
        return "redirect:/admin/matching"
    }

    /** 목 12:00 안내 알림을 지금 서버 시각으로 보낸다. 이미 받은 회원에게는 다시 가지 않는다. */
    @PostMapping("/admin/matching/send-secret-invite")
    fun sendSecretInvite(redirectAttributes: RedirectAttributes): String {
        val sentCount = secretMatchingInviteNotifier.notifyNotParticipated(serverTimeProvider.now())
        if (sentCount > 0) {
            redirectAttributes.addFlashAttribute("message", "시크릿 매칭 안내 알림을 ${sentCount}명에게 보냈습니다.")
        } else {
            redirectAttributes.addFlashAttribute(
                "notice",
                "보낼 대상이 없습니다. 기능 플래그 꺼짐, 이번 주 1:1 퀴즈셋 없음, 남은 라운드 없음, 이미 보냄 중 하나입니다.",
            )
        }
        return "redirect:/admin/matching"
    }

    private fun addSecretRoundFlash(redirectAttributes: RedirectAttributes, now: LocalDateTime, result: SecretRoundBatchResult) {
        val serverTime = now.format(FLASH_TIME_FORMAT)
        when (result.status) {
            SecretRoundBatchResult.Status.FLAG_OFF ->
                redirectAttributes.addFlashAttribute("notice", "시크릿 매칭 기능 플래그가 꺼져 있어 돌리지 않았습니다.")
            SecretRoundBatchResult.Status.NO_ROUND_DUE ->
                redirectAttributes.addFlashAttribute("notice", "서버 시각 $serverTime 기준으로 아직 돌 차례인 시크릿 매칭 라운드가 없습니다.")
            SecretRoundBatchResult.Status.RAN -> {
                // 후보도 정규 메움도 없으면 한 일이 없어서 성공색 대신 안내로 보여 준다.
                val changedAnything = result.backfilledRegularQuizSetIds.isNotEmpty() ||
                    result.roundResults.any { it.savedCount > 0 }
                redirectAttributes.addFlashAttribute(if (changedAnything) "message" else "notice", secretRoundSummary(serverTime, result))
                if (result.failedQuizSetIds.isNotEmpty()) {
                    val failedIds = result.failedQuizSetIds.joinToString(", ") { "#$it" }
                    redirectAttributes.addFlashAttribute("error", "시크릿 매칭: 퀴즈셋 $failedIds 처리에 실패했습니다. 서버 로그를 확인하세요.")
                }
            }
        }
    }

    private fun secretRoundSummary(serverTime: String, result: SecretRoundBatchResult): String {
        val secretRound = requireNotNull(result.secretRound) { "라운드를 돌렸는데 라운드가 없다" }
        val parts = mutableListOf("서버 시각 $serverTime 기준 ${secretRound.description}")
        if (result.backfilledRegularQuizSetIds.isNotEmpty()) {
            val ids = result.backfilledRegularQuizSetIds.joinToString(", ") { "#$it" }
            parts += "후보가 없던 정기 매칭을 먼저 돌렸습니다(퀴즈셋 $ids)"
        }
        result.roundResults.forEach { roundResult ->
            parts += if (roundResult.alreadyGenerated) {
                "퀴즈셋 #${roundResult.quizSetId}: 이미 돈 라운드라 건너뜀"
            } else {
                "퀴즈셋 #${roundResult.quizSetId}: 후보 추가 ${roundResult.newCandidateOwnerIds.size}명 / 대상 ${roundResult.poolSize}명(미성사 회원)"
            }
        }
        if (result.roundResults.isEmpty() && result.failedQuizSetIds.isEmpty()) {
            parts += "이번 주 활성 1:1 퀴즈셋이 없습니다"
        }
        return parts.joinToString(" · ")
    }

    /**
     * 후보 다시 만들기. 성공하면 알림도 함께 남긴다 — 이번에 처음 후보를 받은 회원에게는 알려야 한다.
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
                        "퀴즈셋 #$id 매칭 후보를 다시 만들었습니다.",
                    )
                    redirectAttributes.addFlashAttribute("regeneration", summary)
                    OneToOneCandidateCounts.of(summary)?.let {
                        redirectAttributes.addFlashAttribute("candidateCounts", it)
                    }
                },
                onFailure = { exception ->
                    if (exception !is WarnException) throw exception
                    redirectAttributes.addFlashAttribute("error", regenerationFailedMessage(id, exception))
                },
            )
        return "redirect:/admin/matching"
    }

    private fun regenerationFailedMessage(quizSetId: Long, exception: WarnException): String {
        val failed = "퀴즈셋 #$quizSetId 매칭 다시 만들기 실패: ${exception.message}"
        if (exception.errorCode !in RESETTABLE_BY_QA_TOOLS) return failed
        // 매칭 기록 초기화는 QA 도구라 꺼진 서버에서는 안내할 곳이 없다.
        if (!adminQaToolsProperties.enabled) return failed
        return "$failed 퀴즈셋 상세의 QA 도구에서 [매칭 기록 초기화]를 한 뒤 다시 만드세요."
    }

    companion object {
        private val FLASH_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val RESETTABLE_BY_QA_TOOLS = setOf(
            ErrorCode.MATCH_CANDIDATES_ALREADY_RESPONDED,
            ErrorCode.MATCH_CANDIDATES_SECRET_ROUND_STARTED,
        )
    }
}
