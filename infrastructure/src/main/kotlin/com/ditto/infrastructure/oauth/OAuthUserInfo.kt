package com.ditto.infrastructure.oauth

import com.ditto.domain.member.entity.Gender
import java.time.LocalDate

data class OAuthUserInfo(
    val id: String,
    val nickname: String,
    val email: String?,
    val birthDate: LocalDate? = null,
    val name: String? = null,
    val phoneNumber: String? = null,
    val gender: Gender? = null,
)
