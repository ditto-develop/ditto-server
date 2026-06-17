package com.ditto.domain.member.entity

enum class MemberRole(val description: String) {
    USER("일반 회원"),
    ADMIN("관리자"),
    ;

    /** 화면 표기용 라벨. 예: `USER(일반 회원)` */
    val label: String get() = "$name($description)"
}
