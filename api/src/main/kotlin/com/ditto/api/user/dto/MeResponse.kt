package com.ditto.api.user.dto

import com.ditto.domain.member.entity.Member
import java.time.LocalDate
import java.time.LocalDateTime

data class MeResponse(
    val email: String?,
    val birthDate: LocalDate?,
    val name: String?,
    val phoneNumber: String?,
    val gender: String?,
    /** 프로필 수정에서 닉네임을 더 바꿀 수 있는 횟수. 잠겨 있으면 0. */
    val nicknameChangeRemaining: Int,
    /** 닉네임 변경 잠금이 풀리는 시각. 잠겨 있지 않으면 null. */
    val nicknameChangeLockedUntil: LocalDateTime?,
)

fun Member.toMeResponse(now: LocalDateTime) = MeResponse(
    email = email,
    birthDate = birthDate?.toLocalDate(),
    name = name,
    phoneNumber = phoneNumber,
    gender = gender?.name,
    nicknameChangeRemaining = remainingNicknameChanges(now),
    nicknameChangeLockedUntil = nicknameChangeLockedUntilAt(now),
)
