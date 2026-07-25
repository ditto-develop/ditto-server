package com.ditto.api.chat.websocket

/**
 * 채팅 STOMP destination 규칙을 한곳에 모아 컨트롤러 매핑과 인터셉터 인가가 어긋나지 않게 한다.
 * - 클라이언트 전송: `/pub/chat/rooms/{roomId}` (APP_PREFIX + SEND_MAPPING)
 * - 클라이언트 구독: `/sub/chat/rooms/{roomId}` (roomTopic)
 */
object ChatStompDestinations {
    const val APP_PREFIX = "/pub"
    const val BROKER_PREFIX = "/sub"

    /** `@MessageMapping` 경로 (APP_PREFIX 하위 상대경로). */
    const val SEND_MAPPING = "/chat/rooms/{roomId}"

    private const val ROOM_TOPIC_PREFIX = "/sub/chat/rooms/"
    private val ROOM_TOPIC_REGEX = Regex("^/sub/chat/rooms/(\\d+)$")

    fun roomTopic(roomId: Long): String = "$ROOM_TOPIC_PREFIX$roomId"

    /** 구독 destination 이 방 토픽이면 roomId, 아니면 null. */
    fun parseRoomId(destination: String?): Long? =
        destination?.let { ROOM_TOPIC_REGEX.matchEntire(it)?.groupValues?.get(1)?.toLong() }
}
