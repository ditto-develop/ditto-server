package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaReportForm
import com.ditto.api.system.ServerTimeProvider
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
    private val serverTimeProvider: ServerTimeProvider,
) {
    @PostMapping("/admin/qa/reports")
    fun reportAsDummy(@ModelAttribute form: QaReportForm, redirectAttributes: RedirectAttributes): String {
        redirectAttributes.addFlashAttribute(FROM_REPORT_CARD, true)
        val dummyId = form.dummyId
        val request = form.toRequestOrNull()
        if (dummyId == null || request == null) {
            redirectAttributes.flashRejection("신고하는 더미와 신고할 회원을 골라야 신고할 수 있습니다.")
            return QaRoutes.REPORT_SECTION
        }

        val members = qaMemberLabels.load(listOf(dummyId, request.reportedMemberId))
        val actionLabel = "${members.of(request.reportedMemberId).label} 신고"
        redirectAttributes.flashDummyActionWithResult(members.of(dummyId), actionLabel) {
            validateLikeApp(request)
            val response = userReportController.createUserReport(
                request,
                qaDummies.activePrincipalOf(dummyId, serverTimeProvider.now()),
            )
            response.data?.let { "신고 #${it.id}" }
        }
        return QaRoutes.REPORT_SECTION
    }

    /** 컨트롤러를 직접 부르면 @Valid 가 돌지 않아 앱과 같은 요청 검증을 여기서 한다. */
    private fun validateLikeApp(request: CreateUserReportRequest) {
        val messages = validator.validate(request).map { it.message }.sorted()
        if (messages.isEmpty()) return
        throw WarnException(ErrorCode.BAD_REQUEST, messages.joinToString(" "))
    }

    companion object {
        /** 결과 알림을 신고 카드 안에도 그리라는 표시. 카드가 맨 아래라 위쪽 알림은 화면 밖이다. */
        const val FROM_REPORT_CARD = "fromReportCard"
    }
}
