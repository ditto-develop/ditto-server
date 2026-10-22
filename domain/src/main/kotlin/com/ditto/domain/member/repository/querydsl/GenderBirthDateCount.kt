package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.Gender
import java.time.LocalDate

data class GenderBirthDateCount(
    val gender: Gender?,
    val birthDate: LocalDate?,
    val count: Long,
)
