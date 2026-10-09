package com.ditto.infrastructure.oauth

import com.ditto.domain.member.entity.Gender
import java.time.LocalDate

data class OAuthUserInfo(
    val id: String,
    val nickname: String,
    val email: String?,
    val birthDate: LocalDate? = null,
    val name: String?,
    val phoneNumber: String?,
    val gender: Gender?,
    // 애플만 채운다. 인가 코드를 교환하고 토큰을 폐기할 때 쓸 client_id 다.
    val clientId: String? = null,
)
