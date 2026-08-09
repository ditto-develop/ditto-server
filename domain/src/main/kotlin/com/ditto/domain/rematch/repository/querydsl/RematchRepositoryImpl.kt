package com.ditto.domain.rematch.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.entity.QChatRoom.chatRoom
import com.ditto.domain.rematch.entity.QRematch.rematch
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.entity.RematchStatus
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class RematchRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : RematchRepositoryCustom {

    override fun findMatchedWithoutChatRoom(limit: Int): List<Rematch> =
        queryFactory
            .selectFrom(rematch)
            .where(
                rematch.status.eq(RematchStatus.MATCHED),
                queryFactory.selectOne()
                    .from(chatRoom)
                    .where(
                        chatRoom.sourceType.eq(ChatRoomType.REMATCH),
                        chatRoom.sourceId.eq(rematch.id),
                    )
                    .notExists(),
            )
            // 오래 밀린 것부터 — 가장 오래 기다린 쌍이 먼저 방을 얻는다.
            .orderBy(rematch.matchedAt.asc(), rematch.id.asc())
            .limit(limit.toLong())
            .fetch()

    override fun existsMatchedWithoutChatRoomOfMember(memberId: Long): Boolean =
        queryFactory
            .selectOne()
            .from(rematch)
            .where(
                rematch.status.eq(RematchStatus.MATCHED),
                rematch.memberId1.eq(memberId).or(rematch.memberId2.eq(memberId)),
                queryFactory.selectOne()
                    .from(chatRoom)
                    .where(
                        chatRoom.sourceType.eq(ChatRoomType.REMATCH),
                        chatRoom.sourceId.eq(rematch.id),
                    )
                    .notExists(),
            )
            .fetchFirst() != null
}
