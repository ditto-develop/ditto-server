package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.common.exception.WarnException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.web.servlet.mvc.support.RedirectAttributes

private val log = KotlinLogging.logger {}

internal fun RedirectAttributes.flashDummyAction(dummy: QaMember, action: String, block: () -> Unit) =
    flashDummyActionWithResult(dummy, action) {
        block()
        null
    }

/**
 * 앱이 받는 거부(WarnException)는 코드와 함께 화면에 보여준다. QA 중에는 그 거부 자체가 확인할 대상이다.
 * 성공하면 block 이 돌려준 결과(재매칭 성사 등)를 완료 메시지 뒤에 붙인다.
 */
internal fun RedirectAttributes.flashDummyActionWithResult(dummy: QaMember, action: String, block: () -> String?) {
    val actionLabel = "${dummy.label} · $action"
    runRejectable(block).fold(
        onSuccess = { result -> flashSuccess(actionLabel, result) },
        onFailure = { rejection -> flashRejection("$actionLabel 실패: ${(rejection as WarnException).toDisplayText()}") },
    )
}

internal fun RedirectAttributes.flashEachDummyAction(
    action: String,
    dummies: List<QaMember>,
    block: (QaMember) -> Unit,
) =
    flashEachDummyActionWithResults(action, dummies) { dummy ->
        block(dummy)
        emptyList()
    }

/**
 * 더미마다 따로 실행한다. 앞 더미가 거부돼도 나머지는 계속한다.
 * block 이 돌려준 결과(재매칭 성사 등)는 종류별 건수로 묶어 메시지 뒤에 붙인다.
 */
internal fun RedirectAttributes.flashEachDummyActionWithResults(
    action: String,
    dummies: List<QaMember>,
    block: (QaMember) -> List<String>,
) {
    if (dummies.isEmpty()) {
        addFlashAttribute("error", "$action: 대상 더미가 없습니다.")
        return
    }

    val attempts = dummies.map { dummy -> dummy to runRejectable { block(dummy) } }
    val resultSummary = summarize(attempts.flatMap { (_, attempt) -> attempt.getOrDefault(emptyList()) })
    val rejections = attempts.mapNotNull { (dummy, attempt) ->
        (attempt.exceptionOrNull() as WarnException?)?.let { "${dummy.label} ${it.toDisplayText()}" }
    }
    if (rejections.isEmpty()) {
        flashSuccess("$action (${dummies.size}명)", resultSummary)
        return
    }
    val succeededCount = dummies.size - rejections.size
    val failureText = "$action: ${succeededCount}명 성공, ${rejections.size}명 실패. ${rejections.joinToString(", ")}"
    flashRejection(listOfNotNull(failureText, resultSummary).joinToString(" · "))
}

/** 앱의 거부(WarnException)만 결과로 담는다. 그 밖의 예외는 서버 오류라 그대로 던진다. */
internal fun <T> runRejectable(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it !is WarnException) throw it }

private fun summarize(results: List<String>): String? =
    results.groupingBy { it }.eachCount()
        .map { (result, count) -> "$result ${count}건" }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")

/** 앱이 받는 거부라 서버 오류 알람을 울리지 않게 INFO 로 남긴다. */
internal fun RedirectAttributes.flashRejection(text: String) {
    log.info { "QA 콘솔 거부: $text" }
    addFlashAttribute("error", text)
}

private fun RedirectAttributes.flashSuccess(actionLabel: String, result: String?) {
    val text = if (result == null) "$actionLabel 완료" else "$actionLabel 완료($result)"
    log.info { "QA 콘솔: $text" }
    addFlashAttribute("message", text)
}

private fun WarnException.toDisplayText(): String = "$message (코드 ${errorCode.code})"
