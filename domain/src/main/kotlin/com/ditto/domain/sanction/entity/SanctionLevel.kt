package com.ditto.domain.sanction.entity

/**
 * 제재 수위. 차수 산정(같은 회원의 유효 제재 수 + 1)은 어드민 화면의 추천값일 뿐,
 * 최종 수위는 어드민이 확정한다 — 중대 위반은 차수와 무관하게 PERMANENT_BAN 직접 선택.
 *
 * 선언 순서 = 수위 오름차순 (여러 제재 중 가장 무거운 것을 고를 때 이 순서에 의존한다).
 */
enum class SanctionLevel(val description: String) {
    WARNING("경고 — 다음 주 퀴즈 참여 불가"),
    SUSPENSION("기간 이용 정지"),
    PERMANENT_BAN("영구 차단"),
}
