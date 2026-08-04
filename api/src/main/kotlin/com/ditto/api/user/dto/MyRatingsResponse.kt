package com.ditto.api.user.dto

import java.time.LocalDateTime

/**
 * 내 프로필의 "받은 평가" 카드.
 *
 * 총 평가가 [publicThreshold]건 미만이면 비공개다 — 화면은 "평가가 충분하지 않아요"만 노출하므로
 * 평균·코멘트·노쇼를 내려보내지 않는다([totalCount]만 실제 값).
 *
 * 별점 반올림(.5 이상 올림)·코멘트 3개 노출·`(전체 − 3)` 표기는 FE가 처리한다.
 */
data class MyRatingsResponse(
    val averageScore: Double,
    val totalCount: Long,
    val publicThreshold: Int,
    val noShowCount: Long,
    val ratings: List<MyRatingItem>,
)

data class MyRatingItem(
    val comment: String?,
    val createdAt: LocalDateTime,
)
