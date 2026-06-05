package com.ditto.api.user.dto

import com.ditto.domain.member.entity.Member
import java.time.LocalDate

data class MeResponse(
    val email: String?,
    val birthDate: LocalDate?,
    val name: String?,
    val phoneNumber: String?,
    val gender: String?,
)

fun Member.toMeResponse() = MeResponse(
    email = email,
    birthDate = birthDate?.toLocalDate(),
    name = name,
    phoneNumber = phoneNumber,
    gender = gender?.name,
)
