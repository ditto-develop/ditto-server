package com.ditto.api.match.dto

import com.ditto.domain.match.entity.GroupMatch

/** 그룹 초대 수락 결과. 성사되면 금요일에 채팅방이 열린다. */
data class GroupMatchAcceptResponse(
    val groupMatchId: Long,
    val quizSetId: Long,
    /** 지금까지 수락한 인원 */
    val acceptedCount: Int,
    /** 성사 여부 (수락 인원이 최소 인원에 도달) */
    val isFormed: Boolean,
) {
    companion object {
        fun from(room: GroupMatch): GroupMatchAcceptResponse = GroupMatchAcceptResponse(
            groupMatchId = room.id,
            quizSetId = room.quizSetId,
            acceptedCount = room.acceptedCount,
            isFormed = room.isActive,
        )
    }
}
