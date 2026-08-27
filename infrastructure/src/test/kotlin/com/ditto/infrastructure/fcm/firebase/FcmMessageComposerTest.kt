package com.ditto.infrastructure.fcm.firebase

import com.ditto.infrastructure.fcm.PushMessage
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.MulticastMessage
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import java.time.Duration

/**
 * 앱이 실제로 받는 payload 의 필드 배치를 회귀로 잡는다.
 * SDK 메시지 클래스에 getter 가 없어 리플렉션으로 읽는다 — SDK 업그레이드에서 깨지면 헬퍼만 고친다.
 */
class FcmMessageComposerTest : FreeSpec({

    fun message(unreadCount: Int? = null, ttl: Duration? = null) = PushMessage(
        tokens = listOf("token-1"),
        title = "제목",
        body = "본문",
        data = mapOf("deepLink" to "/matching/"),
        unreadCount = unreadCount,
        ttl = ttl,
    )

    fun <T> Any.readField(name: String): T {
        var type: Class<*>? = this.javaClass
        while (type != null) {
            runCatching { type!!.getDeclaredField(name) }.getOrNull()?.let { field ->
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return field.get(this) as T
            }
            type = type.superclass
        }
        error("필드 없음: $name — SDK 내부 구조가 바뀌었다. 헬퍼를 SDK 에 맞춰 갱신할 것")
    }

    fun androidOf(multicast: MulticastMessage): AndroidConfig = multicast.readField("androidConfig")
    fun apnsOf(multicast: MulticastMessage): ApnsConfig = multicast.readField("apnsConfig")
    fun apsFieldsOf(apns: ApnsConfig): Map<String, Any> {
        val payload: Map<String, Any> = apns.readField("payload")
        @Suppress("UNCHECKED_CAST")
        return payload["aps"] as Map<String, Any>
    }

    fun headersOf(apns: ApnsConfig): Map<String, String> = apns.readField<Map<String, String>?>("headers") ?: emptyMap()

    "공통 내용 — 토큰·data 가 그대로 실린다" {
        val multicast = FcmMessageComposer.toMulticast(message(), listOf("token-1", "token-2"))

        multicast.readField<List<String>>("tokens") shouldBe listOf("token-1", "token-2")
        multicast.readField<Map<String, String>>("data") shouldBe mapOf("deepLink" to "/matching/")
    }

    "Android — 우선순위는 항상 HIGH 다 (Doze 모드 지연 방지)" {
        val multicast = FcmMessageComposer.toMulticast(message(), listOf("token-1"))

        androidOf(multicast).readField<String>("priority") shouldBe "high"
    }

    "unreadCount 는 APNs 뱃지로 간다" {
        val multicast = FcmMessageComposer.toMulticast(message(unreadCount = 7), listOf("token-1"))

        apsFieldsOf(apnsOf(multicast))["badge"] shouldBe 7
    }

    "unreadCount 가 없으면 뱃지를 건드리지 않는다" {
        val multicast = FcmMessageComposer.toMulticast(message(unreadCount = null), listOf("token-1"))

        apsFieldsOf(apnsOf(multicast)) shouldNotContainKey "badge"
    }

    "ttl 은 Android ttl 과 APNs apns-expiration 양쪽으로 간다" {
        val multicast = FcmMessageComposer.toMulticast(message(ttl = Duration.ofHours(1)), listOf("token-1"))

        androidOf(multicast).readField<String>("ttl") shouldBe "3600s"
        val expiration = headersOf(apnsOf(multicast))["apns-expiration"]!!.toLong()
        // 절대 시각(epoch 초) — 지금 + 1시간 부근이면 된다.
        val expected = System.currentTimeMillis() / 1000 + 3600
        (expiration in (expected - 60)..(expected + 60)) shouldBe true
    }

    "ttl 이 없으면 어느 쪽에도 싣지 않는다 — FCM 기본(4주)을 따른다" {
        val multicast = FcmMessageComposer.toMulticast(message(ttl = null), listOf("token-1"))

        androidOf(multicast).readField<String?>("ttl") shouldBe null
        headersOf(apnsOf(multicast)) shouldNotContainKey "apns-expiration"
    }
})
