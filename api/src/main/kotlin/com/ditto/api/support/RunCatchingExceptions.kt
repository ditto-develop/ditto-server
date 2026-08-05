package com.ditto.api.support

/**
 * [runCatching]과 같지만 [Error]는 삼키지 않는다.
 *
 * 항목 하나의 실패를 흡수하고 다음 항목으로 넘어가야 하는 배치·복구 루프가 쓴다 — 그런 곳에서는 실패를
 * 값으로 받는 편이 읽기 좋다. 다만 `runCatching`은 [Throwable]을 잡으므로 `OutOfMemoryError` 같은 치명
 * 오류까지 로그 한 줄로 묻히고, 이미 불안정한 JVM 에서 다음 항목 처리를 계속 시도하게 된다.
 * 그건 통과시킨다.
 *
 * `onFailure` 안의 `throw`는 [Result]로 감싸이지 않고 호출부까지 그대로 전파된다.
 *
 * `inline`을 붙이지 않는다 — 붙이면 호출부로 인라인돼 이 파일에 실행될 바이트코드가 남지 않고,
 * 커버리지 도구가 영구 미커버로 집계한다. 람다 할당 하나를 아끼는 이득보다 그 손해가 크다.
 *
 * `common` 모듈이 더 어울리는 위치이지만 두 사용처가 모두 `api` 라 여기 둔다. `common` 에는 아직 테스트가
 * 없어서, 파일 하나를 옮기면 그 모듈의 커버리지 게이트(50%)가 활성화되며 무관한 클래스까지 끌고 들어온다.
 */
fun <T> runCatchingExceptions(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it !is Exception) throw it }
