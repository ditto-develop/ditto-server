package com.ditto.domain.chat.repository.querydsl

import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.entity.QChatMessage.chatMessage
import com.ditto.domain.chat.entity.QChatRoom.chatRoom
import com.ditto.domain.chat.entity.QChatRoomMember.chatRoomMember
import com.ditto.domain.match.entity.QGroupMatch.groupMatch
import com.ditto.domain.match.entity.QPersonalMatch.personalMatch
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.entity.QNotification.notification
import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.ditto.domain.rematch.entity.QRematch.rematch
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
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

    override fun countMembersPerOpenedRoomInWeek(weekStartedOn: LocalDate): List<ChatRoomMemberCount> {
        val countExpression = chatRoomMember.count()
        return queryFactory
            .select(chatRoom.id, chatRoom.sourceType, countExpression)
            .from(chatRoom)
            .join(chatRoomMember).on(chatRoomMember.roomId.eq(chatRoom.id))
            .where(
                chatRoom.status.ne(ChatRoomStatus.SCHEDULED),
                sourceQuizSetActiveInWeek(weekStartedOn),
                noAdminMember(),
            )
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

    override fun countSendersPerOpenedRoomInWeek(weekStartedOn: LocalDate): List<ChatRoomSenderCount> {
        val countExpression = chatMessage.senderId.countDistinct()
        return queryFactory
            .select(chatRoom.id, countExpression)
            .from(chatRoom)
            .join(chatMessage).on(
                chatMessage.roomId.eq(chatRoom.id),
                chatMessage.messageType.ne(ChatMessageType.SYSTEM),
            )
            .where(
                chatRoom.status.ne(ChatRoomStatus.SCHEDULED),
                sourceQuizSetActiveInWeek(weekStartedOn),
                noAdminMember(),
            )
            .groupBy(chatRoom.id)
            .fetch()
            .map { row ->
                ChatRoomSenderCount(
                    roomId = row.get(chatRoom.id) ?: throw IllegalStateException("group by 키 id 가 비었다"),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    // 방의 회차는 원본 매칭의 퀴즈셋 주다. 재매칭 방도 원래 그룹의 퀴즈셋을 따른다.
    private fun sourceQuizSetActiveInWeek(weekStartedOn: LocalDate): BooleanExpression =
        Expressions.anyOf(
            chatRoom.sourceType.eq(ChatRoomType.PERSONAL).and(
                queryFactory.selectOne()
                    .from(personalMatch)
                    .join(quizSet).on(quizSet.id.eq(personalMatch.quizSetId))
                    .where(personalMatch.id.eq(chatRoom.sourceId), activeQuizSetInWeek(weekStartedOn))
                    .exists(),
            ),
            chatRoom.sourceType.eq(ChatRoomType.GROUP).and(
                queryFactory.selectOne()
                    .from(groupMatch)
                    .join(quizSet).on(quizSet.id.eq(groupMatch.quizSetId))
                    .where(groupMatch.id.eq(chatRoom.sourceId), activeQuizSetInWeek(weekStartedOn))
                    .exists(),
            ),
            chatRoom.sourceType.eq(ChatRoomType.REMATCH).and(
                queryFactory.selectOne()
                    .from(rematch)
                    .join(quizSet).on(quizSet.id.eq(rematch.quizSetId))
                    .where(rematch.id.eq(chatRoom.sourceId), activeQuizSetInWeek(weekStartedOn))
                    .exists(),
            ),
        )

    private fun activeQuizSetInWeek(weekStartedOn: LocalDate): BooleanExpression =
        quizSet.isActive.isTrue.and(quizSet.weekStartedOn.eq(weekStartedOn))

    // 다른 장처럼 관리자를 빼려고 관리자가 든 방은 통째로 뺀다.
    private fun noAdminMember(): BooleanExpression =
        queryFactory.selectOne()
            .from(chatRoomMember)
            .join(member).on(member.id.eq(chatRoomMember.memberId))
            .where(chatRoomMember.roomId.eq(chatRoom.id), member.role.eq(MemberRole.ADMIN))
            .notExists()
}
