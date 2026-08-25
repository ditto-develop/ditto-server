package com.ditto.domain.chat.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.QChatRoom.chatRoom
import com.ditto.domain.chat.entity.QChatRoomMember.chatRoomMember
import com.querydsl.jpa.impl.JPAQueryFactory
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
            )
            .fetch()

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
}
