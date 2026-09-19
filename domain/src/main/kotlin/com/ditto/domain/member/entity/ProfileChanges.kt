package com.ditto.domain.member.entity

/** 프로필 수정 화면(`6.1.1`)에서 바꿀 수 있는 항목. 나이는 생년월일 파생값이라, 이메일은 소셜 로그인 식별자라 없다. */
data class ProfileChanges(
    val nickname: String? = null,
    val gender: Gender? = null,
    val location: Location? = null,
    val job: Job? = null,
    val caricature: String? = null,
    val interests: Set<Interest>? = null,
)
