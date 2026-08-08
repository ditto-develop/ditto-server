package com.ditto.api.review.service

import com.ditto.api.review.dto.RematchResultResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.entity.MemberReview
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 평가 제출에 실린 재매칭 의사를 재매칭 쪽에 반영한다. 재매칭을 받지 않는 평가(1:1)는 아무것도 하지 않으므로,
 * 호출자는 평가 유형을 따지지 않고 그대로 부르면 된다.
 *
 * 리뷰 서비스가 쌍 정규화 규칙과 재매칭 리포지토리를 직접 알지 않도록 분리했다
 * (같은 이유로 매칭 관계 판정은 `MatchAccessChecker`가 맡는다).
 *
 * 행 잠금을 걸므로 호출자 트랜잭션 안에서만 쓴다. 잠금 순서는 평가 → 재매칭 (ADR 0011).
 */
@Component
class RematchSubmitter(
    private val rematchRepository: RematchRepository,
) {
    /** 재매칭 의사를 확정한다. 성사 여부는 [findMatched]로 확인한다. */
    fun submit(
        review: MemberReview,
        memberId: Long,
        counterpartId: Long,
        wants: Boolean?,
        now: LocalDateTime,
    ) {
        val rematch = findWithLock(review, memberId, counterpartId) ?: return
        val submitted = validateWants(wants)
        // 상대가 탈퇴해 성사될 수 없는 쌍이면 의사만 버리고 평가 제출은 성공시킨다.
        // 거부하면 남은 회원이 그 대상 평가를 영구히 확정할 수 없어 그룹 평가가 미완료로 남는다.
        if (rematch.isCancelledByMemberLeave()) {
            return
        }
        rematch.submitWants(memberId, submitted, now)
    }

    /** 재제출한 의사가 기존 확정과 같기를 요구한다 — 다르면 [ErrorCode.REVIEW_ANSWER_NOT_MODIFIABLE]로 거부한다. */
    fun validateUnchanged(
        review: MemberReview,
        memberId: Long,
        counterpartId: Long,
        wants: Boolean?,
    ) {
        val rematch = findWithLock(review, memberId, counterpartId) ?: return
        val submitted = validateWants(wants)
        // 탈퇴로 취소된 뒤 처음 도착한 제출은 의사를 저장하지 않았으므로 비교할 값이 없다.
        // 취소 전에 이미 확정한 의사가 있으면 그대로 비교한다 — "확정한 의사는 수정할 수 없다"는 계약이
        // 취소됐다는 이유로 풀리지는 않는다.
        if (rematch.isCancelledByMemberLeave() && rematch.wantsOf(memberId) == null) {
            return
        }
        if (!rematch.hasSameWants(memberId, submitted)) {
            throw WarnException(ErrorCode.REVIEW_ANSWER_NOT_MODIFIABLE, "확정한 재매칭 의사는 수정할 수 없습니다.")
        }
    }

    /**
     * 상호 성사된 재매칭을 찾는다. 성사되지 않았거나 재매칭을 받지 않는 평가면 `null`이다 —
     * 상대가 아직 고르지 않은 것과 선택하지 않은 것을 이 값으로 구분할 수 없어야 한다.
     *
     * 성사됐다는 것은 양쪽이 모두 선택했다는 뜻이라, 결과를 돌려줘도 상대의 선택이 새로 드러나지는 않는다.
     */
    fun findMatched(review: MemberReview, memberId: Long, counterpartId: Long): RematchResultResponse? {
        val matchedAt = findWithLock(review, memberId, counterpartId)?.matchedAt() ?: return null
        return RematchResultResponse(matchedMemberId = counterpartId, matchedAt = matchedAt)
    }

    /** 재매칭을 받지 않는 평가면 잠글 행도 없다. */
    private fun findWithLock(review: MemberReview, memberId: Long, counterpartId: Long): Rematch? {
        if (!review.canRematch()) {
            return null
        }
        return rematchRepository.findWithLockBySourceGroupMatchIdAndMemberId1AndMemberId2(
            sourceGroupMatchId = review.matchId,
            memberId1 = minOf(memberId, counterpartId),
            memberId2 = maxOf(memberId, counterpartId),
        ) ?: throw WarnException(ErrorCode.INVALID_REVIEW_TARGET, "재매칭 대상이 아닙니다.")
    }

    /** 여기 도달했다는 것은 재매칭을 받는 평가라는 뜻이므로, 의사가 없으면 요청에서 빠진 것이다. */
    private fun validateWants(wants: Boolean?): Boolean =
        wants ?: throw WarnException(ErrorCode.INVALID_REVIEW_ANSWER, "그룹 평가는 재매칭 의사가 필요합니다.")
}
