package com.ditto.infrastructure.fcm.firebase

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ditto.infrastructure.fcm.PushMessage
import com.google.api.core.ApiFutures
import com.google.api.core.SettableApiFuture
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.SendResponse
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.slf4j.LoggerFactory

class FirebaseFcmSenderTest : FreeSpec({

    fun success(): SendResponse = mockk {
        every { exception } returns null
    }

    fun failure(code: MessagingErrorCode?): SendResponse = mockk {
        every { exception } returns mockk<FirebaseMessagingException> {
            every { messagingErrorCode } returns code
        }
    }

    fun batchOf(vararg responses: SendResponse): BatchResponse = mockk {
        every { this@mockk.responses } returns responses.toList()
        every { failureCount } returns responses.count { it.exception != null }
    }

    fun message(tokens: List<String>, type: String? = null) = PushMessage(
        tokens = tokens,
        title = "제목",
        body = "본문",
        data = type?.let { mapOf(PushMessage.DATA_KEY_TYPE to it) }.orEmpty(),
    )

    fun capturedLogs(block: () -> Unit): List<String> {
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        val logger = LoggerFactory.getLogger(DeadTokenCallback::class.java) as Logger
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.map { it.formattedMessage }
    }

    "발송 결과 처리" - {
        "무효 토큰(UNREGISTERED)만 골라 onDeadTokens 로 돌려준다" {
            val firebaseMessaging = mockk<FirebaseMessaging> {
                // 응답 순서 = 요청 토큰 순서: alive(성공), dead(무효), flaky(일시 실패)
                every { sendEachForMulticastAsync(any()) } returns ApiFutures.immediateFuture(
                    batchOf(success(), failure(MessagingErrorCode.UNREGISTERED), failure(MessagingErrorCode.UNAVAILABLE)),
                )
            }
            var deadTokens = emptyList<String>()

            FirebaseFcmSender(firebaseMessaging)
                .send(message(listOf("alive", "dead", "flaky"))) { deadTokens = it }

            // 일시 실패(UNAVAILABLE)는 지우면 안 된다 — 다음 발송에서 성공할 수 있다.
            deadTokens shouldBe listOf("dead")
        }

        "무효 토큰이 아닌 실패는 에러코드·알림 유형과 함께 남긴다" {
            val firebaseMessaging = mockk<FirebaseMessaging> {
                every { sendEachForMulticastAsync(any()) } returns ApiFutures.immediateFuture(
                    batchOf(failure(MessagingErrorCode.SENDER_ID_MISMATCH)),
                )
            }

            val logs = capturedLogs {
                FirebaseFcmSender(firebaseMessaging).send(message(listOf("abcd-token-bad"), type = "CHAT_MESSAGE"))
            }

            logs.single() shouldContain "푸시 일부 실패 1건"
            logs.single() shouldContain "type=CHAT_MESSAGE"
            logs.single() shouldContain "SENDER_ID_MISMATCH(…oken-bad)"
            // 토큰 전체는 비밀값이라 앞부분이 로그에 남으면 안 된다.
            logs.single() shouldNotContain "abcd"
        }

        "에러코드가 없는 실패는 UNKNOWN 으로 남긴다" {
            val firebaseMessaging = mockk<FirebaseMessaging> {
                every { sendEachForMulticastAsync(any()) } returns ApiFutures.immediateFuture(batchOf(failure(null)))
            }

            val logs = capturedLogs {
                FirebaseFcmSender(firebaseMessaging).send(message(listOf("token-bad")))
            }

            logs.single() shouldContain "UNKNOWN(…oken-bad)"
        }

        "무효 토큰(UNREGISTERED)만 실패하면 경고를 남기지 않는다" {
            val firebaseMessaging = mockk<FirebaseMessaging> {
                every { sendEachForMulticastAsync(any()) } returns ApiFutures.immediateFuture(
                    batchOf(success(), failure(MessagingErrorCode.UNREGISTERED)),
                )
            }

            val logs = capturedLogs {
                FirebaseFcmSender(firebaseMessaging).send(message(listOf("alive", "dead")))
            }

            logs shouldBe emptyList()
        }

        "전부 성공이면 onDeadTokens 를 부르지 않는다" {
            val firebaseMessaging = mockk<FirebaseMessaging> {
                every { sendEachForMulticastAsync(any()) } returns ApiFutures.immediateFuture(batchOf(success()))
            }
            var called = false

            FirebaseFcmSender(firebaseMessaging).send(message(listOf("alive"))) { called = true }

            called shouldBe false
        }

        "발송 자체가 실패해도 예외가 밖으로 나가지 않는다" {
            val failedFuture = SettableApiFuture.create<BatchResponse>()
            failedFuture.setException(RuntimeException("fcm down"))
            val firebaseMessaging = mockk<FirebaseMessaging> {
                every { sendEachForMulticastAsync(any()) } returns failedFuture
            }

            shouldNotThrowAny {
                FirebaseFcmSender(firebaseMessaging).send(message(listOf("alive")))
            }
        }
    }

    "500개 제한" - {
        "토큰이 500개를 넘으면 쪼개서 보낸다" {
            val firebaseMessaging = mockk<FirebaseMessaging> {
                every { sendEachForMulticastAsync(any()) } returns ApiFutures.immediateFuture(batchOf(success()))
            }

            FirebaseFcmSender(firebaseMessaging).send(message((1..501).map { "token-$it" }))

            verify(exactly = 2) { firebaseMessaging.sendEachForMulticastAsync(any()) }
        }
    }
})
