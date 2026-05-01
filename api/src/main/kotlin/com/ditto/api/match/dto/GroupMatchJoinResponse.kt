package com.ditto.api.match.dto

import com.ditto.domain.match.entity.GroupMatch

data class GroupMatchJoinResponse(
    val roomId: Long,
    val quizSetId: Long,
    val participantCount: Int,
    val isActive: Boolean,
) {
    companion object {
        fun from(room: GroupMatch): GroupMatchJoinResponse = GroupMatchJoinResponse(
            roomId = room.id,
            quizSetId = room.quizSetId,
            participantCount = room.participantCount,
            isActive = room.isActive,
        )
    }
}
