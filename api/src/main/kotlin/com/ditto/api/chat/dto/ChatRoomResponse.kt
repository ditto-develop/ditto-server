package com.ditto.api.chat.dto

import com.ditto.domain.chat.entity.ChatEndReason
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.review.entity.MemberReview
import java.time.LocalDateTime

data class ChatRoomResponse(
    val roomId: Long,
    val sourceType: ChatRoomType,
    // 나를 제외한, 아직 방에 남아 있는 참여자들. 1:1이면 1명, 그룹이면 여러 명 (이탈자는 빠진다).
    val counterpartMemberIds: List<Long>,
    val lastMessage: ChatMessageResponse?,
    val unreadCount: Long,
    val createdAt: LocalDateTime,
    val opensAt: LocalDateTime,
    // 자동 종료 예정 시각. 남은 시간 카운트다운의 기준이며 연장되면 뒤로 밀린다.
    val expiresAt: LocalDateTime,
    val isEnded: Boolean,
    // 서버 시각 기준 방 상태(SCHEDULED 개방 전 / ACTIVE / ENDED). opensAt 을 기기 시계와 비교해 추측하지 않게 준다.
    val status: ChatRoomStatus,
    // 아래 둘은 종료된 방에만 값이 있다. "누가" 끝냈는지는 대화의 SYSTEM 메시지가 답한다.
    val endedAt: LocalDateTime?,
    val endedReason: ChatEndReason?,
    // 내가 이 방을 나갔는지. 나간 방은 목록에 남지만 읽기 전용이다.
    val hasLeft: Boolean,
    /**
     * 그룹 방의 기본 이름 — 그 그룹이 만들어진 **그룹 퀴즈의 주제**다.
     * 1:1·재매칭은 상대가 한 명이라 이름을 두지 않는다(null).
     */
    val roomName: String?,
    // 이 방의 내 평가 상태. "이미 끝냄(COMPLETED)"과 "아직 안 열림(NOT_OPENED)"을 가른다.
    val reviewStatus: ChatRoomReviewStatus,
    // 평가가 열렸으면 그 ID (제출 경로에 쓴다). 열리지 않았거나 평가가 없는 방이면 null.
    val reviewId: Long?,
    // 이 방 알림을 껐는지. 꺼도 알림 센터에는 쌓이고 푸시만 가지 않는다.
    val isMuted: Boolean,
) {
    companion object {
        fun of(
            room: ChatRoom,
            counterpartMemberIds: List<Long>,
            lastMessage: ChatMessageResponse?,
            unreadCount: Long,
            hasLeft: Boolean,
            roomName: String?,
            myReview: MemberReview?,
            isMuted: Boolean,
        ): ChatRoomResponse = ChatRoomResponse(
            roomId = room.id,
            sourceType = room.sourceType,
            counterpartMemberIds = counterpartMemberIds,
            lastMessage = lastMessage,
            unreadCount = unreadCount,
            createdAt = room.createdAt,
            opensAt = room.opensAt,
            expiresAt = room.expiresAt,
            isEnded = room.isEnded,
            status = room.status,
            endedAt = room.endedAt,
            endedReason = room.endReason,
            hasLeft = hasLeft,
            roomName = roomName,
            reviewStatus = ChatRoomReviewStatus.of(room, myReview),
            reviewId = myReview?.id,
            isMuted = isMuted,
        )
    }
}
