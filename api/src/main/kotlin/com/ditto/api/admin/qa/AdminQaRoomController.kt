package com.ditto.api.admin.qa

import com.ditto.api.chat.controller.ChatController
import com.ditto.api.chat.dto.ChatReadRequest
import com.ditto.api.chat.dto.ChatSendRequest
import com.ditto.api.chat.websocket.ChatStompController
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** QA 콘솔의 채팅방 화면과 더미의 전송(STOMP 핸들러)·읽음·나가기·종료. */
@Controller
class AdminQaRoomController(
    private val adminQaRoomService: AdminQaRoomService,
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val chatController: ChatController,
    private val chatStompController: ChatStompController,
) {
    @GetMapping("/admin/qa/rooms/{roomId}")
    fun page(@PathVariable roomId: Long, model: Model, redirectAttributes: RedirectAttributes): String {
        val room = adminQaRoomService.findRoom(roomId)
        if (room == null) {
            redirectAttributes.addFlashAttribute("error", "채팅방 #$roomId 이 없습니다.")
            return QaRoutes.ROOMS_SECTION
        }
        model.addAttribute("room", room)
        model.addAttribute("timelineSize", AdminQaRoomService.TIMELINE_SIZE)
        model.addAttribute("messagePresets", QaMessagePreset.entries)
        model.addAttribute("sampleVoteDescription", QaSampleVote.description)
        model.addAttribute("active", "qa")
        return "qa/room"
    }

    /** 빠른 입력([preset])이 있으면 [content] 대신 그 메시지들을 보낸다. 중간에 거부되면 거기서 멈춘다. */
    @PostMapping("/admin/qa/rooms/{roomId}/messages")
    fun sendMessages(
        @PathVariable roomId: Long,
        @RequestParam dummyId: Long,
        @RequestParam(required = false) content: String?,
        @RequestParam(required = false) preset: QaMessagePreset?,
        redirectAttributes: RedirectAttributes,
    ): String {
        val contents = preset?.contents() ?: listOf(content.orEmpty())
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "메시지 ${contents.size}개 전송") {
            val sender = authenticationOf(dummyId)
            contents.forEach { chatStompController.sendMessage(roomId, ChatSendRequest(content = it), sender) }
        }
        return QaRoutes.room(roomId)
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/rooms/{roomId}/read")
    fun readAsDummy(
        @PathVariable dummyId: Long,
        @PathVariable roomId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "최신 메시지까지 읽음") {
            readLatest(dummyId, roomId)
        }
        return QaRoutes.room(roomId)
    }

    @PostMapping("/admin/qa/rooms/{roomId}/read-all-dummies")
    fun readAsAllDummies(@PathVariable roomId: Long, redirectAttributes: RedirectAttributes): String {
        redirectAttributes.flashEachDummyAction(
            "방 #$roomId 더미 모두 읽음",
            adminQaRoomService.findActiveDummiesIn(roomId),
        ) { dummy -> readLatest(dummy.id, roomId) }
        return QaRoutes.room(roomId)
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/rooms/{roomId}/leave")
    fun leaveAsDummy(
        @PathVariable dummyId: Long,
        @PathVariable roomId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "방 #$roomId 나가기") {
            chatController.leave(qaDummies.principalOf(dummyId), roomId)
        }
        return QaRoutes.room(roomId)
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/rooms/{roomId}/end")
    fun endAsDummy(
        @PathVariable dummyId: Long,
        @PathVariable roomId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "방 #$roomId 채팅 종료") {
            chatController.end(qaDummies.principalOf(dummyId), roomId)
        }
        return QaRoutes.room(roomId)
    }

    private fun readLatest(dummyId: Long, roomId: Long) {
        val latestMessageId = adminQaRoomService.findLatestMessageId(roomId)
            ?: throw WarnException(ErrorCode.BAD_REQUEST, "읽을 메시지가 없습니다.")
        chatController.read(qaDummies.principalOf(dummyId), roomId, ChatReadRequest(latestMessageId))
    }

    /** STOMP 핸들러는 세션 principal 을 받으므로 앱 연결과 같은 모양으로 만든다. */
    private fun authenticationOf(dummyId: Long): Authentication =
        UsernamePasswordAuthenticationToken(qaDummies.principalOf(dummyId), null, emptyList())
}
