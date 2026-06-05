package com.ditto.api.user.dto

import com.ditto.domain.member.entity.Member
import java.time.LocalDateTime

data class RegisterResponse(
    val id: Long,
    val name: String?,
    val nickname: String,
    val phoneNumber: String?,
    val email: String?,
    val gender: String?,
    val age: Int?,
    val birthDate: LocalDateTime?,
    // FE와 주고받는 code 기준으로 노출한다.
    val interests: List<String>,
    val location: String?,
    val job: String?,
    val joinedAt: LocalDateTime?,
    val role: Any? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

fun Member.toRegisterResponse() = RegisterResponse(
    id = id,
    name = name,
    nickname = nickname,
    phoneNumber = phoneNumber,
    email = email,
    gender = gender?.name,
    age = age,
    birthDate = birthDate,
    interests = interests.map { it.code },
    location = location?.code,
    job = job?.code,
    joinedAt = joinedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
