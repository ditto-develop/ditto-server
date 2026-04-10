package com.ditto.api.user.dto

import com.ditto.domain.member.entity.Member
import java.time.LocalDateTime

data class LeaveResponse(
    val id: Long,
    val name: String?,
    val nickname: String,
    val phoneNumber: String?,
    val email: String?,
    val gender: String?,
    val age: Int?,
    val birthDate: LocalDateTime?,
    val joinedAt: LocalDateTime?,
    val role: Any? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

fun Member.toLeaveResponse() = LeaveResponse(
    id = id,
    name = name,
    nickname = nickname,
    phoneNumber = phoneNumber,
    email = email,
    gender = gender?.name,
    age = age,
    birthDate = birthDate,
    joinedAt = joinedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
