package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaReportForm
import com.ditto.api.userreport.controller.UserReportController
import com.ditto.api.userreport.dto.CreateUserReportRequest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import jakarta.validation.Validator
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** 더미의 신고. 앱 신고 API 를 더미로 불러 중복 신고 거부·차단 생성까지 앱과 똑같이 일어난다. */
@Controller
class AdminQaReportController(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val userReportController: UserReportController,
    private val validator: Validator,
) {
    @PostMapping("/admin/qa/reports")
    fun reportAsDummy(@ModelAttribute form: QaReportForm, redirectAttributes: RedirectAttributes): String {
        val dummyId = form.dummyId
        val targetId = form.targetMemberId
        if (dummyId == null || targetId == null) {
            redirectAttributes.addFlashAttribute("error", "신고하는 더미와 신고받을 회원을 골라 주세요.")
            return QaRoutes.REPORT_SECTION
        }

        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "${qaMemberLabels.one(targetId).label} 신고") {
            val request = form.toRequest(targetId).also(::validateLikeApp)
            userReportController.createUserReport(request, qaDummies.principalOf(dummyId))
        }
        return QaRoutes.REPORT_SECTION
    }

    /** 컨트롤러를 직접 부르면 @Valid 가 돌지 않아 앱과 같은 요청 검증을 여기서 한다. */
    private fun validateLikeApp(request: CreateUserReportRequest) {
        val violation = validator.validate(request).firstOrNull() ?: return
        throw WarnException(ErrorCode.BAD_REQUEST, violation.message)
    }
}
