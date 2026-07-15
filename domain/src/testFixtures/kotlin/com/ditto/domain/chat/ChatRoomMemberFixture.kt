package com.ditto.domain.chat

import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.withId

object ChatRoomMemberFixture {

    fun create(roomId: Long = 1L, memberId: Long = 1L, id: Long = 0L): ChatRoomMember =
        ChatRoomMember.of(roomId = roomId, memberId = memberId).withId(id)
}
