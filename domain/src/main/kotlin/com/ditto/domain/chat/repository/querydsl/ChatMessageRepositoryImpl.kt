package com.ditto.domain.chat.repository.querydsl

import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.QChatMessage.chatMessage
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class ChatMessageRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : ChatMessageRepositoryCustom {

    override fun findByRoomIdWithCursor(roomId: Long, cursor: Long?, size: Int): List<ChatMessage> =
        queryFactory
            .selectFrom(chatMessage)
            .where(
                chatMessage.roomId.eq(roomId),
                cursor?.let { chatMessage.id.lt(it) },
            )
            .orderBy(chatMessage.id.desc())
            .limit(size.toLong())
            .fetch()
}
