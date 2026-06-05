package com.ditto.infrastructure.oauth

import java.time.LocalDate

data class OAuthUserInfo(
    val id: String,
    val nickname: String,
    val email: String?,
    val birthDate: LocalDate? = null,
)
