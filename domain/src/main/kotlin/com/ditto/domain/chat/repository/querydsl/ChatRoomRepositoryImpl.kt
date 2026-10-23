package com.ditto.domain.chat.repository.querydsl

import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.entity.QChatMessage.chatMessage
import com.ditto.domain.chat.entity.QChatRoom.chatRoom
import com.ditto.domain.chat.entity.QChatRoomMember.chatRoomMember
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.entity.QNotification.notification
import com.ditto.domain.rematch.entity.QRematch.rematch
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.LocalDate
import java.time.LocalDateTime
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class ChatRoomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : ChatRoomRepositoryCustom {

    override fun findAllIdsDueToEnd(at: LocalDateTime): List<Long> =
        queryFactory
            .select(chatRoom.id)
            .from(chatRoom)
            .where(
                chatRoom.status.ne(ChatRoomStatus.ENDED),
                chatRoom.expiresAt.loe(at),
            )
            .fetch()

    override fun findAllIdsDueToOpen(at: LocalDateTime): List<Long> =
        queryFactory
            .select(chatRoom.id)
            .from(chatRoom)
            .where(
                chatRoom.status.eq(ChatRoomStatus.SCHEDULED),
                chatRoom.opensAt.loe(at),
            )
            .fetch()

    override fun findAllIdsEndingBetween(from: LocalDateTime, to: LocalDateTime): List<Long> =
        queryFactory
            .select(chatRoom.id)
            .from(chatRoom)
            .where(
                chatRoom.status.eq(ChatRoomStatus.ACTIVE),
                chatRoom.expiresAt.gt(from),
                chatRoom.expiresAt.loe(to),
                notAlreadyNotified(NotificationType.CHAT_ENDING_SOON),
            )
            .fetch()

    override fun findAllIdsSilentOpenedBetween(from: LocalDateTime, to: LocalDateTime): List<Long> =
        queryFactory
            .select(chatRoom.id)
            .from(chatRoom)
            .where(
                chatRoom.status.eq(ChatRoomStatus.ACTIVE),
                chatRoom.opensAt.gt(from),
                chatRoom.opensAt.loe(to),
                queryFactory.selectOne()
                    .from(chatMessage)
                    .where(
                        chatMessage.roomId.eq(chatRoom.id),
                        chatMessage.messageType.ne(ChatMessageType.SYSTEM),
                    )
                    .notExists(),
                notAlreadyNotified(NotificationType.CHAT_NO_MESSAGE),
            )
            .fetch()

    /**
     * 이 방으로 [type] 알림이 아직 없다. 매분 도는 조회가 이미 알린 방의 멤버마다 존재 검사를 반복하지 않게 한다.
     * 방 단위 판정이라 한 명이라도 적재됐으면 그 방은 다시 보지 않는다. 적재에 일부만 실패해도 재시도하지 않는다.
     */
    private fun notAlreadyNotified(type: NotificationType): BooleanExpression =
        queryFactory.selectOne()
            .from(notification)
            .where(notification.type.eq(type), notification.targetId.eq(chatRoom.id))
            .notExists()

    override fun existsUnendedRoomOfMember(memberId: Long): Boolean =
        queryFactory
            .selectOne()
            .from(chatRoom)
            .join(chatRoomMember).on(chatRoomMember.roomId.eq(chatRoom.id))
            .where(
                chatRoomMember.memberId.eq(memberId),
                // 내가 나간 방은 진행 중이어도 나를 붙잡지 않는다 — 안 걸러내면 방을 다 나간 회원도 탈퇴가 막힌다.
                chatRoomMember.leftAt.isNull,
                chatRoom.status.ne(ChatRoomStatus.ENDED),
            )
            .fetchFirst() != null

    override fun countMembersPerOpenedRoomOfWeek(weekStartedOn: LocalDate): List<ChatRoomMemberCount> {
        val countExpression = chatRoomMember.count()
        return queryFactory
            .select(chatRoom.id, chatRoom.sourceType, countExpression)
            .from(chatRoom)
            .join(chatRoomMember).on(chatRoomMember.roomId.eq(chatRoom.id))
            .where(openedRoomOfWeek(weekStartedOn))
            .groupBy(chatRoom.id, chatRoom.sourceType)
            .fetch()
            .map { row ->
                ChatRoomMemberCount(
                    roomId = row.get(chatRoom.id) ?: throw IllegalStateException("group by 키 id 가 비었다"),
                    roomType = row.get(chatRoom.sourceType)
                        ?: throw IllegalStateException("group by 키 sourceType 이 비었다"),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    override fun countSendersPerOpenedRoomOfWeek(weekStartedOn: LocalDate): List<ChatRoomSenderCount> {
        val countExpression = chatMessage.senderId.countDistinct()
        return queryFactory
            .select(chatRoom.id, countExpression)
            .from(chatRoom)
            .join(chatMessage).on(
                chatMessage.roomId.eq(chatRoom.id),
                chatMessage.messageType.ne(ChatMessageType.SYSTEM),
            )
            .where(openedRoomOfWeek(weekStartedOn))
            .groupBy(chatRoom.id)
            .fetch()
            .map { row ->
                ChatRoomSenderCount(
                    roomId = row.get(chatRoom.id) ?: throw IllegalStateException("group by 키 id 가 비었다"),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    // 1:1·그룹 방은 매칭 주 금요일에 열린다. 재매칭 방은 나중 주에 열려서 원래 그룹의 주로 센다.
    private fun openedRoomOfWeek(weekStartedOn: LocalDate): BooleanExpression {
        val weekStart = weekStartedOn.atStartOfDay()
        val openedInWeek = chatRoom.sourceType.ne(ChatRoomType.REMATCH)
            .and(chatRoom.opensAt.goe(weekStart))
            .and(chatRoom.opensAt.lt(weekStart.plusWeeks(1)))
        val rematchOfWeek = chatRoom.sourceType.eq(ChatRoomType.REMATCH)
            .and(
                queryFactory.selectOne()
                    .from(rematch)
                    .where(rematch.id.eq(chatRoom.sourceId), rematch.weekStartedOn.eq(weekStartedOn))
                    .exists(),
            )
        return chatRoom.status.ne(ChatRoomStatus.SCHEDULED).and(openedInWeek.or(rematchOfWeek))
    }
}
