package com.ditto.api.admin.qa

import com.ditto.api.chat.controller.ChatVoteController
import com.ditto.api.chat.dto.ChatVoteCastRequest
import com.ditto.api.system.ServerTimeProvider
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** QA 콘솔 방 화면의 그룹 투표 생성·투표·마감. */
@Controller
class AdminQaVoteController(
    private val adminQaRoomService: AdminQaRoomService,
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val serverTimeProvider: ServerTimeProvider,
    private val chatVoteController: ChatVoteController,
) {
    @PostMapping("/admin/qa/rooms/{roomId}/votes")
    fun createSampleVote(
        @PathVariable roomId: Long,
        @RequestParam dummyId: Long,
        @RequestParam(defaultValue = "false") allowMultiple: Boolean,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "샘플 투표 만들기") {
            val request = QaSampleVote.request(allowMultiple, serverTimeProvider.now())
            chatVoteController.createVote(qaDummies.requireActiveDummyPrincipal(dummyId), roomId, request)
        }
        return QaRoutes.room(roomId)
    }

    @PostMapping("/admin/qa/rooms/{roomId}/votes/{voteId}/cast")
    fun cast(
        @PathVariable roomId: Long,
        @PathVariable voteId: Long,
        @RequestParam dummyId: Long,
        @RequestParam(required = false) placeIds: List<Long>?,
        @RequestParam(required = false) timeIds: List<Long>?,
        redirectAttributes: RedirectAttributes,
    ): String {
        // 빈 목록은 그 유형의 표를 취소한다는 뜻이라, 고르지 않고 누르면 기존 표가 조용히 지워진다.
        if (placeIds.isNullOrEmpty() || timeIds.isNullOrEmpty()) {
            redirectAttributes.addFlashAttribute("error", "장소와 시간을 하나 이상씩 골라야 투표할 수 있습니다.")
            return QaRoutes.room(roomId)
        }

        val request = ChatVoteCastRequest(placeIds = placeIds, timeIds = timeIds)
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "투표 #${voteId}에 투표") {
            chatVoteController.cast(qaDummies.requireActiveDummyPrincipal(dummyId), roomId, voteId, request)
        }
        return QaRoutes.room(roomId)
    }

    @PostMapping("/admin/qa/rooms/{roomId}/votes/{voteId}/cast-random-all-dummies")
    fun castRandomlyForAllDummies(
        @PathVariable roomId: Long,
        @PathVariable voteId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        val vote = adminQaRoomService.findVote(roomId, voteId)
        if (vote == null) {
            redirectAttributes.addFlashAttribute("error", "이 방에 없는 투표입니다: #$voteId")
            return QaRoutes.room(roomId)
        }

        redirectAttributes.flashEachDummyAction(
            "투표 #$voteId 더미 모두 무작위 투표",
            adminQaRoomService.findActiveDummiesIn(roomId),
        ) { dummy ->
            val voter = qaDummies.requireActiveDummyPrincipal(dummy.id)
            chatVoteController.cast(voter, roomId, voteId, QaRandomCast.of(vote))
        }
        return QaRoutes.room(roomId)
    }

    @PostMapping("/admin/qa/rooms/{roomId}/votes/{voteId}/close")
    fun close(
        @PathVariable roomId: Long,
        @PathVariable voteId: Long,
        @RequestParam dummyId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "투표 #$voteId 마감") {
            chatVoteController.close(qaDummies.requireActiveDummyPrincipal(dummyId), roomId, voteId)
        }
        return QaRoutes.room(roomId)
    }
}
