package com.ditto.domain.member.entity

enum class MemberStatus(private val description: String) {
    PENDING("소셜 로그인만 완료한 상태"),
    ACTIVE("회원가입 완료"),
    SUSPENDED("이용 정지 (suspended_until까지)"),
    BANNED("영구 차단"),
    LEFT("탈퇴 (30일 내 재가입 시 복구 가능)"),
}
