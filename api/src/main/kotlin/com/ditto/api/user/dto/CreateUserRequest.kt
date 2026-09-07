package com.ditto.api.user.dto

import com.ditto.domain.member.entity.Gender
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
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

    // 성별·나이는 매칭의 입력값이라 필수다 — 없으면 후보 풀 구성 단계에서 조용히 제외돼
    // "가입은 됐는데 매칭만 영영 안 되는" 회원이 생긴다. 카카오(비즈 앱) 동의항목이 아니라
    // 온보딩 화면에서 직접 받는다(피그마 1.3.2 회원가입 _ 프로필 작성).
    val gender: Gender,

    // 나이대의 대표값. FE는 구간 중앙값을 보낸다(20~24 → 22, 25~29 → 27 … 60 이상 → 60).
    @field:Min(20)
    @field:Max(100)
    val age: Int,

    // 아래 4개(생년월일·이름·전화번호·이메일)는 일반 앱에서 카카오가 주지 않는 값이라 비워둘 수 있다.
    // 가입 후 `PATCH /api/v1/users/me/personal-info`로 언제든 채울 수 있다.
    val birthDate: LocalDateTime? = null,

    @field:NotEmpty(message = "관심사는 최소 1개 이상 선택해야 합니다.")
    val interests: Set<String>,

    @field:NotBlank
    val location: String,

    @field:NotBlank
    val job: String,

    // 프론트에서 고른 캐리커쳐 문자열을 그대로 저장한다. FE 는 아바타 경로("/onboarding/.../m1.svg")를
    // 싣는다 — 조회(profileImageUrl)가 이 값을 그대로 이미지 주소로 내보내는 것과 맞물린다.
    @field:NotBlank
    @field:Size(max = 100)
    val caricature: String,

    // 한 줄 소개. 소개노트 '나를 한 줄로 표현한다면?' 답변에 저장된다(PATCH 의 introduction 과 같은 위치).
    @field:Size(max = 50)
    val introduction: String? = null,
)
