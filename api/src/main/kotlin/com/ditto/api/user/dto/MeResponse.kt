package com.ditto.api.user.dto

import com.ditto.domain.member.entity.Member
import java.time.LocalDate

data class MeResponse(
    val email: String?,
    // 생년월일은 날짜 개념이므로 시각 없이 LocalDate(yyyy-MM-dd)로 노출한다.
    val birthDate: LocalDate?,
)

fun Member.toMeResponse() = MeResponse(
    email = email,
    birthDate = birthDate?.toLocalDate(),
)
