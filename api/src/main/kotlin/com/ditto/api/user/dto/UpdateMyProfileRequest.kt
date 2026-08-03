package com.ditto.api.user.dto

import jakarta.validation.constraints.Size

/**
 * 마이프로필 수정 요청. 화면(`6.1.1 프로필 수정`)에서 수정 가능한 항목만 받는다 —
 * 닉네임·성별·나이·사는곳·직업은 비활성이라 여기에 없다.
 *
 * **null = 변경 없음**이다. 값이 온 항목만 검증하고 갱신한다.
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
) {
    companion object {
        const val INTRODUCTION_MAX_LENGTH = 50
        const val MAX_INTEREST_COUNT = 5
    }
}
