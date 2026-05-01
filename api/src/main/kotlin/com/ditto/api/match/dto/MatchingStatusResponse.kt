package com.ditto.api.match.dto

import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import java.time.LocalDateTime

data class MatchingStatusResponse(
    val quizSetId: Long,
    /** ACCEPTED > PENDING 순으로 우선 노출. 매칭이 없으면 null */
    val personalMatch: PersonalMatchSummary?,
    /** NONE / JOINED / DECLINED */
    val groupMatchStatus: GroupMatchStatus,
    /** groupMatchStatus == JOINED 일 때 설정 */
    val groupMatchRoomId: Long?,
)

data class PersonalMatchSummary(
    val id: Long,
    val requesterId: Long,
    val receiverId: Long,
    val status: PersonalMatchStatus,
    val createdAt: LocalDateTime,
    val respondedAt: LocalDateTime?,
) {
    companion object {
        fun from(match: PersonalMatch): PersonalMatchSummary = PersonalMatchSummary(
            id = match.id,
            requesterId = match.requesterId,
            receiverId = match.receiverId(),
            status = match.status,
            createdAt = match.createdAt,
            respondedAt = match.respondedAt,
        )
    }
}
