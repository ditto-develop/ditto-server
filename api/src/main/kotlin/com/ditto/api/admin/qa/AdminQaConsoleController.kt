package com.ditto.api.admin.qa

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * QA 콘솔. 로그인할 수 없는 더미 회원을 대신 움직여 1:1·그룹 채팅을 혼자 확인한다.
 * 더미의 행동은 앱이 부르는 API 컨트롤러를 더미 principal 로 그대로 호출한다. 브로드캐스트·알림까지 실제 경로와
 * 같아야 QA 결과를 믿을 수 있어서다(ADR 0038). 동작은 1:1·그룹·방·투표·평가·신고·시각별 컨트롤러가 나눠 맡는다.
 */
@Controller
class AdminQaConsoleController(
    private val adminQaService: AdminQaService,
    private val adminQaRoomService: AdminQaRoomService,
    private val qaDummyReports: QaDummyReports,
) {
    @GetMapping("/admin/qa")
    fun page(model: Model): String {
        val console = adminQaService.getConsole()
        val rooms = adminQaRoomService.getRoomSummaries()
        model.addAttribute("console", console)
        model.addAttribute("rooms", rooms)
        model.addAttribute("reportSection", qaDummyReports.composeSection(console))
        model.addAttribute("active", "qa")
        return "qa/console"
    }
}
