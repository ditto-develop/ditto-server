package com.ditto.domain.member.entity

enum class MemberStatus(private val description: String) {
    PENDING("소셜 로그인만 완료한 상태"),
    ACTIVE("회원가입 완료"),
}
