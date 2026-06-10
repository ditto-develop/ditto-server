package com.ditto.domain.member.entity

enum class MemberRole(private val description: String) {
    USER("일반 회원"),
    ADMIN("관리자"),
}
