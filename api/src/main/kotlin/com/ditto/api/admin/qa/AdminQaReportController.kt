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

/** 더미의 신고. 앱 신고 API 를 더미 principal 로 불러 중복 신고 거부·차단 생성까지 앱과 같게 한다. */
@Controller
class AdminQaReportController(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val userReportController: UserReportController,
    private val validator: Validator,
) {
    @PostMapping("/admin/qa/reports")
    fun reportAsDummy(@ModelAttribute form: QaReportForm, redirectAttributes: RedirectAttributes): String {
        val dummyId = form.dummyId ?: return redirectAttributes.rejectMissingSelection("신고하는 더미를 골라야 신고할 수 있습니다.")
        val request = form.toRequestOrNull()
            ?: return redirectAttributes.rejectMissingSelection("신고할 회원을 고르거나 회원 ID를 넣어야 신고할 수 있습니다.")

        val members = qaMemberLabels.load(listOf(dummyId, request.reportedMemberId))
        redirectAttributes.flashDummyActionWithResult(
            members.of(dummyId),
            "${members.of(request.reportedMemberId).label} 신고",
        ) {
            validateLikeApp(request)
            reportWithRetryHint(request, dummyId)
        }
        return QaRoutes.REPORT_SECTION
    }

    private fun RedirectAttributes.rejectMissingSelection(text: String): String {
        flashRejection(text)
        return QaRoutes.REPORT_SECTION
    }

    /** 컨트롤러를 직접 부르면 @Valid 가 돌지 않아 앱과 같은 요청 검증을 여기서 한다. 문구 순서는 매번 같게 정렬한다. */
    private fun validateLikeApp(request: CreateUserReportRequest) {
        val messages = validator.validate(request).map { it.message }.sorted()
        if (messages.isEmpty()) return
        throw WarnException(ErrorCode.BAD_REQUEST, messages.joinToString(" "))
    }

    private fun reportWithRetryHint(request: CreateUserReportRequest, dummyId: Long): String? =
        runRejectable {
            userReportController.createUserReport(request, qaDummies.requireActiveDummyPrincipal(dummyId))
        }.fold(
            onSuccess = { response -> response.data?.let { "신고 #${it.id}" } },
            onFailure = { rejection -> throw (rejection as WarnException).withRetryHint() },
        )

    private fun WarnException.withRetryHint(): WarnException {
        if (errorCode != ErrorCode.DUPLICATE_REPORT) return this
        return WarnException(errorCode, "$message 검토를 끝내거나 다른 더미로 신고하세요.")
    }
}
