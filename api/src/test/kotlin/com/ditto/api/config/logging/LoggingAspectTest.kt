package com.ditto.api.config.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ditto.api.support.RestDocsTest
import io.kotest.inspectors.forAtLeastOne
import io.kotest.inspectors.forNone
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(TestLoggingController::class, TestLoggingService::class)
class LoggingAspectTest : RestDocsTest() {

    private lateinit var logAppender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUpAppender() {
        logAppender = ListAppender<ILoggingEvent>()
        logAppender.start()
        (LoggerFactory.getLogger(LoggingAspect::class.java) as Logger).addAppender(logAppender)
    }

    @AfterEach
    fun tearDownAppender() {
        (LoggerFactory.getLogger(LoggingAspect::class.java) as Logger).detachAppender(logAppender)
    }

    private fun logs() = logAppender.list.map { it.formattedMessage }

    @Nested
    @DisplayName("기본 로깅")
    inner class BasicLogging {

        @Test
        @DisplayName("진입 로그에 메서드명과 파라미터가 출력된다")
        fun logMethodEntry() {
            mockMvc.perform(get("/api/test/logging/echo").withApiKey().param("name", "tuna"))
                .andExpect(status().isOk)

            logs().forAtLeastOne {
                it shouldContain "--> TestLoggingController.echo"
                it shouldContain "name=tuna"
            }
        }

        @Test
        @DisplayName("반환 로그에 결과값과 실행 시간이 출력된다")
        fun logMethodReturn() {
            mockMvc.perform(get("/api/test/logging/echo").withApiKey().param("name", "tuna"))
                .andExpect(status().isOk)

            logs().forAtLeastOne {
                it shouldContain "<-- TestLoggingController.echo"
                it shouldContain "return: tuna"
                it shouldContain "ms"
            }
        }
    }

    @Nested
    @DisplayName("@Mask 마스킹")
    inner class MaskLogging {

        @Test
        @DisplayName("요청 파라미터에서 @Mask 필드는 ** 로 마스킹된다")
        fun maskRequestField() {
            mockMvc.perform(
                post("/api/test/logging/masked-request").withApiKey()
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"user@test.com","password":"secret123"}"""),
            ).andExpect(status().isOk)

            logs().forAtLeastOne {
                it shouldContain "email=user@test.com"
                it shouldContain "password=**"
            }
            logs().forNone { it shouldContain "secret123" }
        }

        @Test
        @DisplayName("반환값에서 @Mask 필드는 ** 로 마스킹된다")
        fun maskResponseField() {
            mockMvc.perform(get("/api/test/logging/masked-response").withApiKey())
                .andExpect(status().isOk)

            logs().forAtLeastOne {
                it shouldContain "email=user@test.com"
                it shouldContain "token=**"
            }
            logs().forNone { it shouldContain "secret-token" }
        }
    }

    @Nested
    @DisplayName("내부 메서드 로깅")
    inner class InternalCallLogging {

        @Test
        @DisplayName("@Loggable 컨텍스트 내 내부 메서드 호출은 로깅된다")
        fun logInternalCallInsideLoggable() {
            mockMvc.perform(get("/api/test/logging/call-service").withApiKey())
                .andExpect(status().isOk)

            logs().forAtLeastOne { it shouldContain "TestLoggingService.process" }
        }

        @Test
        @DisplayName("@Loggable 컨텍스트 밖의 내부 메서드 호출은 로깅되지 않는다")
        fun skipInternalCallOutsideLoggable() {
            mockMvc.perform(get("/api/test/logging/direct-service").withApiKey())
                .andExpect(status().isOk)

            logs().forNone { it shouldContain "TestLoggingService.process" }
        }
    }
}
