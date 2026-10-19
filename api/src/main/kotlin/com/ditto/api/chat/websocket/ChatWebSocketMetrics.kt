package com.ditto.api.chat.websocket

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component
import org.springframework.web.socket.config.WebSocketMessageBrokerStats
import org.springframework.web.socket.messaging.StompSubProtocolHandler
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler

/**
 * 채팅이 살아 있는지 Grafana에서 보기 위한 WebSocket·STOMP 세션 메트릭.
 * Spring은 [WebSocketMessageBrokerStats]를 30분마다 로그로만 남겨서, 같은 값을 Micrometer로 내보낸다.
 * 아웃바운드 큐(`clientOutboundChannelExecutor`)는 Boot가 이미 `executor_*`로 내보내므로 여기서 다루지 않는다.
 */
@Component
class ChatWebSocketMetrics(
    private val brokerStats: WebSocketMessageBrokerStats,
) : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        Gauge.builder(SESSIONS, brokerStats) { sessionStats(it)?.webSocketSessions?.toDouble() ?: 0.0 }
            .description("현재 열려 있는 WebSocket 세션 수")
            .register(registry)

        // 아래는 누적값이라 카운터로 낸다 — 비정상 종료가 "늘었는지"를 rate로 본다.
        closedSessions(registry, "limit_exceeded") { it.limitExceededSessions }
        closedSessions(registry, "no_messages_received") { it.noMessagesReceivedSessions }
        closedSessions(registry, "transport_error") { it.transportErrorSessions }

        stompFrames(registry, "connect") { it.totalConnect }
        stompFrames(registry, "connected") { it.totalConnected }
        stompFrames(registry, "disconnect") { it.totalDisconnect }
    }

    private fun closedSessions(registry: MeterRegistry, reason: String, value: (SubProtocolWebSocketHandler.Stats) -> Int) {
        FunctionCounter.builder(SESSIONS_CLOSED, brokerStats) { sessionStats(it)?.let(value)?.toDouble() ?: 0.0 }
            .description("비정상 종료된 WebSocket 세션 누적 수")
            .tag("reason", reason)
            .register(registry)
    }

    private fun stompFrames(registry: MeterRegistry, frame: String, value: (StompSubProtocolHandler.Stats) -> Int) {
        FunctionCounter.builder(STOMP_FRAMES, brokerStats) { stompStats(it)?.let(value)?.toDouble() ?: 0.0 }
            .description("클라이언트와 주고받은 STOMP 연결 프레임 누적 수")
            .tag("frame", frame)
            .register(registry)
    }

    // 핸들러가 아직 연결되지 않았으면 Spring이 null을 준다.
    private fun sessionStats(stats: WebSocketMessageBrokerStats): SubProtocolWebSocketHandler.Stats? =
        stats.webSocketSessionStats

    private fun stompStats(stats: WebSocketMessageBrokerStats): StompSubProtocolHandler.Stats? =
        stats.stompSubProtocolStats

    companion object {
        const val SESSIONS = "ditto.websocket.sessions"
        const val SESSIONS_CLOSED = "ditto.websocket.sessions.closed"
        const val STOMP_FRAMES = "ditto.stomp.frames"
    }
}
