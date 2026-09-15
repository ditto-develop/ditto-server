package com.ditto.api.chat

import com.ditto.api.chat.dto.ChatReadEvent
import com.ditto.common.serialization.ObjectMapperFactory
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/** FE 는 type 유무로 READ 프레임과 메시지 프레임을 가르므로, 직렬화 결과에 type 이 실려야 한다. */
class ChatReadEventTest : FreeSpec(
    {
        val objectMapper = ObjectMapperFactory.create()

        "STOMP 프레임 JSON 에 type=READ 와 커서 두 개가 실린다" {
            val event = ChatReadEvent(roomId = 1L, memberId = 2L, previousLastReadMessageId = 7L, lastReadMessageId = 10L)

            val json = objectMapper.readTree(objectMapper.writeValueAsString(event))

            json["type"].asText() shouldBe "READ"
            json["roomId"].asLong() shouldBe 1L
            json["memberId"].asLong() shouldBe 2L
            json["previousLastReadMessageId"].asLong() shouldBe 7L
            json["lastReadMessageId"].asLong() shouldBe 10L
        }

        "처음 읽음이면 previousLastReadMessageId 는 null 로 실린다" {
            val event = ChatReadEvent(roomId = 1L, memberId = 2L, previousLastReadMessageId = null, lastReadMessageId = 10L)

            val json = objectMapper.readTree(objectMapper.writeValueAsString(event))

            json["previousLastReadMessageId"].isNull shouldBe true
        }
    },
)
