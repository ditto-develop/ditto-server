package com.ditto.api.admin.qa

import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.entity.RematchStatus

/** 더미가 의사를 낸 뒤 쌍의 결과. 아직 상대가 내지 않았으면 결과가 없다. */
enum class QaRematchOutcome(val label: String) {
    MATCHED("재매칭 성사"),
    NOT_MUTUAL("재매칭 불성사"),
    CANCELLED_BY_MEMBER_LEAVE("탈퇴로 취소"),
    ;

    companion object {
        fun of(rematch: Rematch): QaRematchOutcome? =
            when (rematch.status) {
                RematchStatus.WAITING -> null
                RematchStatus.MATCHED -> MATCHED
                RematchStatus.CANCELLED ->
                    if (rematch.isCancelledByMemberLeave()) CANCELLED_BY_MEMBER_LEAVE else NOT_MUTUAL
            }
    }
}
