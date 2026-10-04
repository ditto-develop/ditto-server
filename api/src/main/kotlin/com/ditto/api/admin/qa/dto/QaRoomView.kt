package com.ditto.api.admin.qa.dto

import com.ditto.api.admin.qa.QaSystemMessageMeaning
import com.ditto.common.exception.ErrorCode
import com.ditto.domain.chat.entity.ChatEndReason
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.chat.entity.ChatRoom
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

    val isEnded: Boolean = status == ChatRoomStatus.ENDED
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
    /** 두 사람 방은 [채팅 종료], 그룹은 [나가기]. 앱의 [ChatRoom.canEndByUser]를 그대로 받는다. */
    val canEndByUser: Boolean,
    val leaveDissolvesRoom: Boolean,
) {
    val activeDummies: List<QaMember> = members.filter { it.isDummy && !it.hasLeft }.map { it.member }

    /** 그룹은 한 명이 나가도 방이 이어지고, 두 사람 방은 나가는 것이 곧 종료다. */
    val isGroup: Boolean = sourceType == ChatRoomType.GROUP

    val isEnded: Boolean = status == ChatRoomStatus.ENDED

    val isScheduled: Boolean = status == ChatRoomStatus.SCHEDULED

    val notOpenedErrorCode: String = ErrorCode.CHAT_ROOM_NOT_OPENED.code

    val endedErrorCode: String = ErrorCode.CHAT_ROOM_ENDED.code

    /** 개방 전 방을 열어 보려고 서버 시각을 옮길 때 쓰는 시각. 스케줄러가 다음 분에 연다. */
    val justAfterOpen: LocalDateTime = opensAt.plusMinutes(1)

    /** 다음 주 이후에 열리는 방(재매칭 등). 그 시각으로 옮기면 이번 주 열린 방이 모두 마감된다. */
    val opensAfterCurrentWeekend: Boolean = opensAt >= ChatPeriod.weekendOf(now).expiresAt

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

    val isImage: Boolean = messageType == ChatMessageType.IMAGE

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
