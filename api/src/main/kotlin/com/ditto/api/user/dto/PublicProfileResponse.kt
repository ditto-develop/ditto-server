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
    // 아래 3개는 아직 데이터 소스가 없어 null. (rating: 평가 도메인 별도 / preferred: FE 미사용)
    val rating: Double? = null,
    val preferredMinAge: Int? = null,
    val preferredMaxAge: Int? = null,
)
