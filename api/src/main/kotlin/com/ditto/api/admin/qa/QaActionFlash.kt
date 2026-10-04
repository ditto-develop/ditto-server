package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.common.exception.WarnException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.web.servlet.mvc.support.RedirectAttributes

private val log = KotlinLogging.logger {}

/** 앱이 받는 거부(WarnException)는 코드와 함께 화면에 보여준다. QA 중에는 그 거부 자체가 확인할 대상이다. */
internal fun RedirectAttributes.flashDummyAction(dummy: QaMember, action: String, block: () -> Unit) {
    val actionLabel = "${dummy.label} · $action"
    val rejection = rejectionOf(block)
    if (rejection == null) {
        flashSuccess(actionLabel)
        return
    }
    addFlashAttribute("error", "$actionLabel 실패: ${rejection.toDisplayText()}")
}

/** 더미마다 따로 실행한다. 앞 더미가 거부돼도 나머지는 계속한다. */
internal fun RedirectAttributes.flashEachDummyAction(
    action: String,
    dummies: List<QaMember>,
    block: (QaMember) -> Unit,
) {
    if (dummies.isEmpty()) {
        addFlashAttribute("error", "$action: 대상 더미가 없습니다.")
        return
    }

    val rejections = dummies.mapNotNull { dummy ->
        rejectionOf { block(dummy) }?.let { "${dummy.label} ${it.toDisplayText()}" }
    }
    if (rejections.isEmpty()) {
        flashSuccess("$action (${dummies.size}명)")
        return
    }
    val succeededCount = dummies.size - rejections.size
    addFlashAttribute(
        "error",
        "$action: ${succeededCount}명 성공, ${rejections.size}명 실패. ${rejections.joinToString(", ")}",
    )
}

/** 그 밖의 예외는 서버 오류라 그대로 던진다. */
private fun rejectionOf(block: () -> Unit): WarnException? =
    runCatching(block).exceptionOrNull()?.let { it as? WarnException ?: throw it }

private fun RedirectAttributes.flashSuccess(actionLabel: String) {
    log.info { "QA 콘솔: $actionLabel" }
    addFlashAttribute("message", "$actionLabel 완료")
}

private fun WarnException.toDisplayText(): String = "$message (${errorCode.code})"
