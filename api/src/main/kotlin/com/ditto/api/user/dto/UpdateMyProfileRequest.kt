package com.ditto.api.user.dto

import com.ditto.domain.member.entity.Gender
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * 마이프로필 수정 요청(`6.1.1 프로필 수정`). **null = 변경 없음**이다.
 *
 * 나이·이메일은 받지 않는다 — 나이는 생년월일 파생값이고 이메일은 소셜 로그인 식별자다.
 * 생년월일은 `PATCH /api/v1/users/me/personal-info`가 맡는다.
 *
 * [introduction]은 소개노트 `ONE_WORD` 답변에 write-through 된다(프로필 조회가 그 값을 읽는다).
 * 상한 50자는 프로필 수정 화면의 제한이다 — 소개노트 화면은 현재 제한이 없어 더 긴 값이
 * 들어올 수 있고, 그 정합은 후속으로 `IntroQuestion` 단위 상한에서 다룬다(#122 본문).
 */
data class UpdateMyProfileRequest(
    @field:Size(max = INTRODUCTION_MAX_LENGTH, message = "한 줄 소개는 최대 ${INTRODUCTION_MAX_LENGTH}자입니다.")
    val introduction: String? = null,

    @field:Size(max = 100)
    val profileImageUrl: String? = null,

    @field:Size(min = 1, max = MAX_INTEREST_COUNT, message = "관심사는 1~${MAX_INTEREST_COUNT}개 선택해야 합니다.")
    val interests: Set<String>? = null,

    // 가입과 같은 형식 규칙을 쓴다. 중복은 서비스가 저장소를 보고 판단한다.
    @field:Size(min = NICKNAME_MIN_LENGTH, max = NICKNAME_MAX_LENGTH)
    @field:Pattern(regexp = NICKNAME_PATTERN, message = "닉네임은 한글·영문·숫자만 허용됩니다.")
    val nickname: String? = null,

    val gender: Gender? = null,

    // 가입과 같은 code 집합(Location·Job). 매핑은 서비스가 한다.
    val location: String? = null,

    // 서버 엔티티 이름은 job 이지만 FE 계약은 occupation 이다 — 프로필 조회 응답과 이름을 맞춘다.
    val occupation: String? = null,
) {
    companion object {
        const val INTRODUCTION_MAX_LENGTH = 50
        const val MAX_INTEREST_COUNT = 5
        const val NICKNAME_MIN_LENGTH = 2
        const val NICKNAME_MAX_LENGTH = 10
        const val NICKNAME_PATTERN = "^[a-zA-Z0-9가-힣]+$"
    }
}
