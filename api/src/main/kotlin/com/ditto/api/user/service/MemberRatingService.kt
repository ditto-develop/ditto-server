package com.ditto.api.user.service

import com.ditto.api.user.dto.MyRatingItem
import com.ditto.api.user.dto.MyRatingsResponse
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.repository.ReviewAnswerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * "받은 평가" 집계. 내 프로필(`/users/me/ratings`)과 타인 프로필(`/users/{id}/ratings`)이 같은 값을 봐야 하므로
 * 계산과 공개 기준을 여기 한 곳에 둔다 — 화면마다 기준이 달라지면 같은 회원의 평점이 두 갈래가 된다.
 */
@Service
class MemberRatingService(
    private val reviewAnswerRepository: ReviewAnswerRepository,
) {

    @Transactional(readOnly = true)
    fun getRatings(memberId: Long): MyRatingsResponse {
        val received = reviewAnswerRepository
            .findAllByReviewedMemberIdAndAnsweredAtIsNotNullOrderByAnsweredAtDesc(memberId)
        val totalCount = received.size.toLong()

        // 공개 기준 미달이면 총 건수만 알린다 — 화면이 평균·코멘트·노쇼를 렌더하지 않는다.
        if (totalCount < PUBLIC_THRESHOLD) {
            return MyRatingsResponse(
                averageScore = 0.0,
                totalCount = totalCount,
                publicThreshold = PUBLIC_THRESHOLD,
                noShowCount = 0,
                ratings = emptyList(),
            )
        }

        return MyRatingsResponse(
            averageScore = received.mapNotNull { it.rating }.average(),
            totalCount = totalCount,
            publicThreshold = PUBLIC_THRESHOLD,
            noShowCount = received.count { it.meetingStatus == MeetingStatus.NO_SHOW }.toLong(),
            ratings = received.map { answer ->
                MyRatingItem(
                    comment = answer.comment,
                    // 확정 평가만 조회했으므로 answeredAt은 항상 존재한다.
                    createdAt = answer.answeredAt!!,
                )
            },
        )
    }

    /**
     * 프로필 카드에 실을 평균 별점. 공개 기준 미달이면 null이다 —
     * 평가 카드가 "평가가 충분하지 않아요"인데 프로필 상단에만 점수가 뜨면 안 된다.
     */
    @Transactional(readOnly = true)
    fun findPublicAverageScore(memberId: Long): Double? =
        getRatings(memberId).takeIf { it.totalCount >= PUBLIC_THRESHOLD }?.averageScore

    companion object {
        /** 받은 평가 공개 기준 — 3건 미만이면 "평가가 충분하지 않아요"를 노출한다. */
        const val PUBLIC_THRESHOLD = 3
    }
}
