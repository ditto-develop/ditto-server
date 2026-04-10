package com.ditto.api.user.dto

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.socialaccount.entity.SocialProvider
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateUserRequest(

    @field:Size(max = 50)
    val name: String? = null,

    @field:Size(min = 2, max = 10)
    @field:Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임은 한글·영문·숫자만 허용됩니다.")
    val nickname: String? = null,

    @field:Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
    val phoneNumber: String? = null,

    @field:Email
    val email: String? = null,

    val gender: Gender? = null,

    val age: Int? = null,

    val birthDate: LocalDateTime? = null,

    @field:NotNull
    val provider: SocialProvider,

    @field:NotBlank
    val providerUserId: String,
)
