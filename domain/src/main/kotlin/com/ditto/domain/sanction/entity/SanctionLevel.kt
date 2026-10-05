package com.ditto.domain.sanction.entity

/**
 * 제재 종류. 차수(같은 회원의 누적 제재 수 + 1)는 어드민 화면의 참고값일 뿐,
 * 최종 제재 종류는 어드민이 정한다. 중대 위반은 차수와 무관하게 PERMANENT_BAN 을 직접 고른다.
 *
 * 선언 순서 = 가벼운 순 (여러 제재 중 가장 무거운 것을 고를 때 이 순서에 의존한다).
 */
enum class SanctionLevel(val description: String) {
    WARNING("경고 (다음 주 퀴즈 참여 불가)"),
    SUSPENSION("2주 이용 정지"),
    PERMANENT_BAN("영구 차단"),
}
