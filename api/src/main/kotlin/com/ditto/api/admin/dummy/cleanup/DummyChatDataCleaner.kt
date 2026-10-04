package com.ditto.api.admin.dummy.cleanup

import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.chat.repository.ChatVoteChoiceRepository
import com.ditto.domain.chat.repository.ChatVoteOptionRepository
import com.ditto.domain.chat.repository.ChatVoteRepository
import org.springframework.stereotype.Component

/** 더미가 한 명이라도 있던 채팅방은 QA로 만든 방이라 같은 방에 있던 실회원의 메시지·멤버 행까지 통째로 지운다. */
@Component
class DummyChatDataCleaner(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatVoteRepository: ChatVoteRepository,
    private val chatVoteOptionRepository: ChatVoteOptionRepository,
    private val chatVoteChoiceRepository: ChatVoteChoiceRepository,
) {
    /** 지운 방 id 를 돌려준다. 그 방을 가리키는 평가·재매칭·알림을 지우는 데 쓴다. */
    fun deleteRoomsWith(dummyIds: Collection<Long>): Set<Long> {
        val roomIds = chatRoomMemberRepository.findByMemberIdIn(dummyIds).map { it.roomId }.toSet()
        if (roomIds.isEmpty()) return emptySet()

        deleteVotesIn(roomIds)
        chatMessageRepository.deleteAllInBatch(chatMessageRepository.findByRoomIdIn(roomIds))
        chatRoomMemberRepository.deleteAllInBatch(chatRoomMemberRepository.findByRoomIdIn(roomIds))
        chatRoomRepository.deleteAllByIdInBatch(roomIds)
        return roomIds
    }

    private fun deleteVotesIn(roomIds: Set<Long>) {
        val votes = chatVoteRepository.findByRoomIdIn(roomIds)
        if (votes.isEmpty()) return

        val voteIds = votes.map { it.id }
        chatVoteChoiceRepository.deleteAllInBatch(chatVoteChoiceRepository.findByVoteIdIn(voteIds))
        chatVoteOptionRepository.deleteAllInBatch(chatVoteOptionRepository.findByVoteIdIn(voteIds))
        chatVoteRepository.deleteAllInBatch(votes)
    }
}
