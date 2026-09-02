package com.ditto.api.user.dto

/**
 * 타인 공개 프로필 응답. 민감정보(email·phoneNumber·실명)는 포함하지 않는다.
 * location·occupation·interests는 FE와 공유하는 code로 반환한다.
 */
data class PublicProfileResponse(
    val userId: Long,
    val nickname: String,
    val gender: String?,
    val age: Int?,
    // 자기소개는 소개노트 ONE_WORD 답변으로 채운다. 없으면 null.
    val introduction: String?,
    val profileImageUrl: String?,
    val location: String?,
    val occupation: String?,
    val interests: List<String>,
    // 받은 평가 평균. 공개 기준(3건) 미달이면 null — 평가 카드와 같은 기준이다.
    val rating: Double? = null,
    // preferred* 는 FE 미사용이라 아직 데이터 소스가 없다.
    val preferredMinAge: Int? = null,
    val preferredMaxAge: Int? = null,
)
