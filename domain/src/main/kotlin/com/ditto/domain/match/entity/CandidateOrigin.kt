package com.ditto.domain.match.entity

/**
 * 1:1 후보 출처. 시크릿 매칭은 같은 퀴즈셋에 후보를 더한다.
 * SECRET_* 값은 시크릿 매칭 라운드 자체를 가리킬 때도 쓴다. 요일·시각은 `SecretMatchingSchedule`이 정한다.
 */
enum class CandidateOrigin(val description: String) {
    REGULAR("정규 매칭"),
    SECRET_FIRST("시크릿 매칭 1차"),
    SECRET_SECOND("시크릿 매칭 2차"),
}
