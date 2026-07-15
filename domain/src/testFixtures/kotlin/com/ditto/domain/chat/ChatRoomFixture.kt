package com.ditto.domain.chat

import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.withId

object ChatRoomFixture {

    fun personal(sourceId: Long = 1L, id: Long = 0L): ChatRoom =
        ChatRoom.personal(sourceId).withId(id)
}
