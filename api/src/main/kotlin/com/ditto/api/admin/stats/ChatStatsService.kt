package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.ChatStatsData
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 방 단위 지표라 회원 조건은 걸지 않는다. 매칭 유형은 화면이 그 유형 줄만 골라 그린다.
@Service
class ChatStatsService(
    private val chatRoomRepository: ChatRoomRepository,
) {

    @Transactional(readOnly = true)
    fun findStats(week: OperationWeek): ChatStatsData =
        ChatStatsData(
            chatRoomMemberCounts = chatRoomRepository.countMembersPerOpenedRoomInWeek(week.startedOn),
            chatRoomSenderCounts = chatRoomRepository.countSendersPerOpenedRoomInWeek(week.startedOn),
        )
}
