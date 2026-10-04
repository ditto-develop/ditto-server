package com.ditto.api.admin.qa.dto

import com.ditto.api.admin.qa.QaSystemMessageMeaning
import com.ditto.domain.chat.entity.ChatEndReason
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.entity.ChatVoteCloseReason
import java.time.LocalDateTime

/** 콘솔 목록의 한 줄. 인원은 나가지 않은 사람만 센다. */
class QaRoomSummary(
    val roomId: Long,
    val sourceType: ChatRoomType,
    val sourceLabel: String?,
    val status: ChatRoomStatus,
    val opensAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val realMembers: List<QaMember>,
    val dummyCount: Int,
    val lastMessageAt: LocalDateTime?,
) {
    val memberCount: Int = realMembers.size + dummyCount
}

class QaRoomView(
    val now: LocalDateTime,
    val roomId: Long,
    val sourceType: ChatRoomType,
    val status: ChatRoomStatus,
    val opensAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val endReason: ChatEndReason?,
    val members: List<QaRoomMember>,
    val messages: List<QaRoomMessage>,
    val votes: List<QaVote>,
) {
    val activeDummies: List<QaMember> = members.filter { it.isDummy && !it.hasLeft }.map { it.member }

    /** 그룹에 두 명만 남았으면 한 명이 더 나갈 때 방이 해체된다. */
    val lastLeaveDissolvesRoom: Boolean = members.count { !it.hasLeft } == 2

    /** 그룹은 한 명이 나가도 방이 이어지고, 두 사람 방은 나가는 것이 곧 종료다. */
    val isGroup: Boolean = sourceType == ChatRoomType.GROUP

    val isEnded: Boolean = status == ChatRoomStatus.ENDED

    /** 개방 전 방을 열어 보려고 서버 시각을 옮길 때 쓰는 시각. 스케줄러가 다음 분에 연다. */
    val justAfterOpen: LocalDateTime = opensAt.plusMinutes(1)

    val openVote: QaVote? = votes.firstOrNull { it.isOpen }

    val closedVotes: List<QaVote> = votes.filterNot { it.isOpen }
}

class QaRoomMember(
    val member: QaMember,
    val isDummy: Boolean,
    val hasLeft: Boolean,
    val lastReadMessageId: Long?,
)

class QaRoomMessage(
    val messageId: Long,
    val sender: QaMember,
    val isFromDummy: Boolean,
    val messageType: ChatMessageType,
    val content: String,
    val sentAt: LocalDateTime,
    val unreadCount: Int,
) {
    val isSystem: Boolean = messageType == ChatMessageType.SYSTEM

    val systemMeaning: String? = if (isSystem) QaSystemMessageMeaning.of(content) else null
}

/** 그룹 만남 투표. 투표자는 나가지 않은 멤버만 센다(앱 집계와 같다). */
class QaVote(
    val voteId: Long,
    val isOpen: Boolean,
    val allowMultiple: Boolean,
    val votedCount: Int,
    val totalMembers: Int,
    val closedReason: ChatVoteCloseReason?,
    val placeOptions: List<QaVoteOption>,
    val timeOptions: List<QaVoteOption>,
)

class QaVoteOption(
    val optionId: Long,
    val label: String,
    val voters: List<QaMember>,
)
