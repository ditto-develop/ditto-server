package com.ditto.api.chat.websocket

import com.ditto.api.config.auth.CorsProperties
import com.ditto.common.serialization.ObjectMapperFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.converter.MessageConverter
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration

/**
 * STOMP over WebSocket 설정. 단일 인스턴스 동안은 내장 SimpleBroker 로 충분하며,
 * 스케일아웃(2번째 레플리카) 시 enableStompBrokerRelay(RabbitMQ 등)로 교체한다. 배경: ADR 0009.
 * 백프레셔 하드닝: 아웃바운드 큐 유한화 + send/message 크기·시간 상한으로 느린 소비자발 OOM 을 차단.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val stompAuthChannelInterceptor: StompAuthChannelInterceptor,
    private val corsProperties: CorsProperties,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint(WS_ENDPOINT)
            .setAllowedOriginPatterns(*corsProperties.allowedOrigins.toTypedArray())
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.setApplicationDestinationPrefixes(ChatStompDestinations.APP_PREFIX)
        registry.enableSimpleBroker(ChatStompDestinations.BROKER_PREFIX)
            .setHeartbeatValue(longArrayOf(HEARTBEAT_MS, HEARTBEAT_MS))
            .setTaskScheduler(webSocketHeartbeatScheduler())
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompAuthChannelInterceptor)
    }

    override fun configureClientOutboundChannel(registration: ChannelRegistration) {
        // 느린 소비자로 인한 무제한 큐 적재(OOM) 방지 — 아웃바운드 태스크 큐를 유한하게 제한한다.
        registration.taskExecutor()
            .corePoolSize(OUTBOUND_CORE_POOL_SIZE)
            .maxPoolSize(OUTBOUND_MAX_POOL_SIZE)
            .queueCapacity(OUTBOUND_QUEUE_CAPACITY)
    }

    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT)
        registration.setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT)
        registration.setSendTimeLimit(SEND_TIME_LIMIT_MS)
    }

    override fun configureMessageConverters(messageConverters: MutableList<MessageConverter>): Boolean {
        // 프로젝트 표준 ObjectMapper(JSR310 등)로 LocalDateTime 직렬화를 보장.
        messageConverters.add(
            MappingJackson2MessageConverter().apply { objectMapper = ObjectMapperFactory.create() },
        )
        return false
    }

    @Bean
    fun webSocketHeartbeatScheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("ws-heartbeat-")
        }

    companion object {
        private const val WS_ENDPOINT = "/ws"
        private const val HEARTBEAT_MS = 10_000L
        private const val OUTBOUND_CORE_POOL_SIZE = 2
        private const val OUTBOUND_MAX_POOL_SIZE = 4
        private const val OUTBOUND_QUEUE_CAPACITY = 1_000
        private const val MESSAGE_SIZE_LIMIT = 64 * 1024
        private const val SEND_BUFFER_SIZE_LIMIT = 512 * 1024
        private const val SEND_TIME_LIMIT_MS = 10_000
    }
}
