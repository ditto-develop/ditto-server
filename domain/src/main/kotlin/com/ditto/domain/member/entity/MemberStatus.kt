package com.ditto.domain.member.entity

enum class MemberStatus {
    PENDING, // 아직 회원가입을 진행하지 않은 상태(소셜 로그인만 했을 경우)
    ACTIVE,
}
