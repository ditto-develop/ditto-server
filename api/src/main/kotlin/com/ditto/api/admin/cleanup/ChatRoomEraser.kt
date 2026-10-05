package com.ditto.api.admin.cleanup

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.chat.repository.ChatVoteChoiceRepository
import com.ditto.domain.chat.repository.ChatVoteOptionRepository
import com.ditto.domain.chat.repository.ChatVoteRepository
import org.springframework.stereotype.Component

/** 지울 채팅방을 찾고, 방에 딸린 멤버·메시지·투표까지 통째로 지운다. */
@Component
class ChatRoomEraser(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatVoteRepository: ChatVoteRepository,
    private val chatVoteOptionRepository: ChatVoteOptionRepository,
    private val chatVoteChoiceRepository: ChatVoteChoiceRepository,
) {
    /** 한 번이라도 멤버였던 방. 나간 멤버도 행이 남으므로 함께 걸린다. */
    fun findRoomIdsWithMembers(memberIds: Collection<Long>): Set<Long> =
        chatRoomMemberRepository.findByMemberIdIn(memberIds).map { it.roomId }.toSet()

    /** 원본(그룹 매칭·재매칭)이 지워질 방. 남기면 사라진 원본을 가리켜 방 이름·평가 열기가 깨진다. */
    fun findRoomIdsFrom(sourceType: ChatRoomType, sourceIds: Collection<Long>): Set<Long> {
        if (sourceIds.isEmpty()) return emptySet()
        return chatRoomRepository.findBySourceTypeAndSourceIdIn(sourceType, sourceIds).map { it.id }.toSet()
    }

    fun deleteRooms(roomIds: Collection<Long>) {
        if (roomIds.isEmpty()) return

        deleteVotesIn(roomIds)
        chatMessageRepository.deleteAllByIdInBatch(chatMessageRepository.findByRoomIdIn(roomIds).map { it.id })
        chatRoomMemberRepository.deleteAllByIdInBatch(chatRoomMemberRepository.findByRoomIdIn(roomIds).map { it.id })
        chatRoomRepository.deleteAllByIdInBatch(roomIds)
    }

    private fun deleteVotesIn(roomIds: Collection<Long>) {
        val voteIds = chatVoteRepository.findByRoomIdIn(roomIds).map { it.id }
        if (voteIds.isEmpty()) return

        chatVoteChoiceRepository.deleteAllByIdInBatch(chatVoteChoiceRepository.findByVoteIdIn(voteIds).map { it.id })
        chatVoteOptionRepository.deleteAllByIdInBatch(chatVoteOptionRepository.findByVoteIdIn(voteIds).map { it.id })
        chatVoteRepository.deleteAllByIdInBatch(voteIds)
    }
}
