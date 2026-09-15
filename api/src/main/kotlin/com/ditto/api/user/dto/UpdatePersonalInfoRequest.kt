package com.ditto.api.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 가입 때 받지 못한 신원 정보를 나중에 채우는 요청. **모든 항목이 선택**이며 준 값만 반영한다.
 *
 * 이름·전화번호·생년월일은 카카오 일반 앱이 안 주니까 여기서 받는다.
 * 이메일은 로그인 때 카카오 값으로 들어오지만 미동의 회원도 있어서 같이 받는다.
 * 성별·나이는 가입 필수값이라 여기 없다.
 */
data class UpdatePersonalInfoRequest(

    @field:Size(max = 50)
    val name: String? = null,

    @field:Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
    val phoneNumber: String? = null,

    @field:Email
    val email: String? = null,

    @field:Past(message = "생년월일은 과거 날짜여야 합니다.")
    val birthDate: LocalDateTime? = null,
)
