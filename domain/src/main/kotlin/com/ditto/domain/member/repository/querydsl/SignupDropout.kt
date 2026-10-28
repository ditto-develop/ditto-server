package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.socialaccount.entity.SocialProvider
import java.time.LocalDateTime

data class SignupDropout(
    val memberId: Long,
    val signupStartedAt: LocalDateTime,
    val socialProvider: SocialProvider?,
    val hasNicknameReservation: Boolean,
    val hasPushDevice: Boolean,
)
