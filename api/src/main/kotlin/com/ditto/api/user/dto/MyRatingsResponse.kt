package com.ditto.api.user.dto

import java.time.LocalDateTime

/**
 * "받은 평가" 카드. 내 프로필(`/users/me/ratings`)과 타인 프로필(`/users/{id}/ratings`)이 같은 스키마를 쓴다.
 *
 * 총 평가가 [publicThreshold]건 미만이면 비공개다 — 화면은 "평가가 충분하지 않아요"만 노출하므로
 * 평균·코멘트·노쇼를 내려보내지 않는다([totalCount]만 실제 값).
 *
 * 성사 전 후보가 보는 요약본에서는 [noShowCount]가 null 이고 [ratings]가 비어 있다
 * ([com.ditto.api.user.service.ProfileAccessLevel.SUMMARY]). 0 으로 내리면 "노쇼 0회"라고
 * 단언하는 셈이라, 비공개와 실제 0건을 구분할 수 있게 null 로 비운다.
 *
 * 별점 반올림(.5 이상 올림)·코멘트 3개 노출·`(전체 − 3)` 표기는 FE가 처리한다.
 */
data class MyRatingsResponse(
    val averageScore: Double,
    val totalCount: Long,
    val publicThreshold: Int,
    val noShowCount: Long?,
    val ratings: List<MyRatingItem>,
)

data class MyRatingItem(
    val comment: String?,
    val createdAt: LocalDateTime,
)
