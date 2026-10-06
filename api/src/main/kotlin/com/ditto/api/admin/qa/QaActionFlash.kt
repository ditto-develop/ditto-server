package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.common.exception.WarnException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.web.servlet.mvc.support.RedirectAttributes

private val log = KotlinLogging.logger {}

/** 앱이 받는 거부(WarnException)는 코드와 함께 화면에 보여준다. QA 중에는 그 거부 자체가 확인할 대상이다. */
internal fun RedirectAttributes.flashDummyAction(dummy: QaMember, action: String, block: () -> Unit) =
    flashDummyActionWithResult(dummy, action) {
        block()
        null
    }

/** 성공하면 block 이 돌려준 결과(재매칭 성사 등)를 완료 메시지 뒤에 붙인다. */
internal fun RedirectAttributes.flashDummyActionWithResult(dummy: QaMember, action: String, block: () -> String?) {
    val actionLabel = "${dummy.label} · $action"
    var result: String? = null
    val rejection = rejectionOf { result = block() }
    if (rejection == null) {
        flashSuccess(actionLabel, result)
        return
    }
    flashRejection("$actionLabel 실패: ${rejection.toDisplayText()}")
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
    flashRejection("$action: ${succeededCount}명 성공, ${rejections.size}명 실패. ${rejections.joinToString(", ")}")
}

/** 그 밖의 예외는 서버 오류라 그대로 던진다. */
private fun rejectionOf(block: () -> Unit): WarnException? =
    runCatching(block).exceptionOrNull()?.let { it as? WarnException ?: throw it }

/** 앱이 받는 거부라 서버 오류 알람을 울리지 않게 INFO 로 남긴다. */
private fun RedirectAttributes.flashRejection(text: String) {
    log.info { "QA 콘솔 거부: $text" }
    addFlashAttribute("error", text)
}

private fun RedirectAttributes.flashSuccess(actionLabel: String, result: String? = null) {
    val text = listOfNotNull("$actionLabel 완료", result).joinToString(" · ")
    log.info { "QA 콘솔: $text" }
    addFlashAttribute("message", text)
}

private fun WarnException.toDisplayText(): String = "$message (코드 ${errorCode.code})"
