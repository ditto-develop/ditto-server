package com.ditto.common.exception

/**
 * [runCatching]과 같지만 [Error]는 삼키지 않는다.
 *
 * 항목 하나의 실패를 흡수하고 다음 항목으로 넘어가야 하는 배치·복구 루프가 쓴다 — 그런 곳에서는 실패를
 * 값으로 받는 편이 읽기 좋다. 다만 `runCatching`은 [Throwable]을 잡으므로 `OutOfMemoryError` 같은 치명
 * 오류까지 로그 한 줄로 묻히고, 이미 불안정한 JVM 에서 다음 항목 처리를 계속 시도하게 된다.
 * 그건 통과시킨다.
 *
 * `onFailure` 안의 `throw`는 [Result]로 감싸이지 않고 호출부까지 그대로 전파된다.
 */
inline fun <T> runCatchingExceptions(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it !is Exception) throw it }
