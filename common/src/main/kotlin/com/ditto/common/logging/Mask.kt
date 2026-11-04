package com.ditto.common.logging

/**
 * 로그에 값 대신 `**`를 남긴다. DTO 필드에도, 메서드 파라미터에도 붙는다.
 *
 * 파라미터에 붙이는 경우: `@Loggable` 진입점 아래 계층은 토큰 같은 값을 DTO 가 아니라 raw String
 * 인자로 받는다. DTO 의 [Mask]는 거기까지 따라가지 않으므로 그 파라미터에 직접 붙인다.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Mask
