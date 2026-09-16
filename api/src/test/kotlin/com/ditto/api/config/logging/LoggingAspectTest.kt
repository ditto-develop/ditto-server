package com.ditto.api.config.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ditto.api.support.RestDocsTest
import io.kotest.inspectors.forAtLeastOne
import io.kotest.inspectors.forNone
import io.kotest.matchers.shouldBe
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

@Import(TestLoggingController::class, TestClassLoggableController::class, TestLoggingService::class)
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

    private fun levelOf(fragment: String) =
        logAppender.list.first { it.formattedMessage.contains(fragment) }.level.levelStr

    @Nested
    @DisplayName("기본 로깅")
    inner class BasicLogging {

        @Test
        @DisplayName("진입 로그에 메서드명과 파라미터가 출력된다")
        fun logMethodEntry() {
            mockMvc.perform(get("/api/test/logging/echo").withApiKey().withBearerToken().param("name", "tuna"))
                .andExpect(status().isOk)

            logs().forAtLeastOne {
                it shouldContain "--> TestLoggingController.echo"
                it shouldContain "name=tuna"
            }
        }

        @Test
        @DisplayName("반환 로그에 결과값과 실행 시간이 출력된다")
        fun logMethodReturn() {
            mockMvc.perform(get("/api/test/logging/echo").withApiKey().withBearerToken().param("name", "tuna"))
                .andExpect(status().isOk)

            logs().forAtLeastOne {
                it shouldContain "<-- TestLoggingController.echo"
                it shouldContain "return: tuna"
                it shouldContain "ms"
            }
        }
    }

    @Nested
    @DisplayName("예외 로그 레벨")
    inner class ExceptionLevel {

        @Test
        @DisplayName("WarnException은 의도된 비즈니스 응답이라 WARN으로 남는다")
        fun warnExceptionLogsAtWarn() {
            mockMvc.perform(get("/api/test/logging/warn").withApiKey().withBearerToken())
                .andExpect(status().isOk)

            levelOf("<-- TestLoggingController.throwWarn") shouldBe "WARN"
        }

        @Test
        @DisplayName("그 밖의 예외는 ERROR로 남는다")
        fun otherExceptionLogsAtError() {
            mockMvc.perform(get("/api/test/logging/boom").withApiKey().withBearerToken())
                .andExpect(status().isOk)

            levelOf("<-- TestLoggingController.throwUnexpected") shouldBe "ERROR"
        }
    }

    @Nested
    @DisplayName("클래스 레벨 @Loggable")
    inner class ClassLevelLoggable {

        @Test
        @DisplayName("클래스에 붙이면 핸들러마다 붙이지 않아도 진입·반환이 로깅된다")
        fun logsWithoutMethodAnnotation() {
            mockMvc.perform(
                get("/api/test/logging/class-level").withApiKey().withBearerToken().param("name", "tuna"),
            ).andExpect(status().isOk)

            logs().forAtLeastOne { it shouldContain "--> TestClassLoggableController.classLevel" }
            logs().forAtLeastOne { it shouldContain "<-- TestClassLoggableController.classLevel" }
        }

        @Test
        @DisplayName("클래스 레벨 진입점도 한 번만 로깅된다 (두 어드바이스 중복 방지)")
        fun logsOnlyOnce() {
            mockMvc.perform(
                get("/api/test/logging/class-level").withApiKey().withBearerToken().param("name", "tuna"),
            ).andExpect(status().isOk)

            logs().count { it.contains("--> TestClassLoggableController.classLevel") } shouldBe 1
        }
    }

    @Nested
    @DisplayName("@Mask 마스킹")
    inner class MaskLogging {

        @Test
        @DisplayName("요청 파라미터에서 @Mask 필드는 ** 로 마스킹된다")
        fun maskRequestField() {
            mockMvc.perform(
                post("/api/test/logging/masked-request").withApiKey().withBearerToken()
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
            mockMvc.perform(get("/api/test/logging/masked-response").withApiKey().withBearerToken())
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
            mockMvc.perform(get("/api/test/logging/call-service").withApiKey().withBearerToken())
                .andExpect(status().isOk)

            logs().forAtLeastOne { it shouldContain "TestLoggingService.process" }
        }

        @Test
        @DisplayName("@Loggable 컨텍스트 밖의 내부 메서드 호출은 로깅되지 않는다")
        fun skipInternalCallOutsideLoggable() {
            mockMvc.perform(get("/api/test/logging/direct-service").withApiKey().withBearerToken())
                .andExpect(status().isOk)

            logs().forNone { it shouldContain "TestLoggingService.process" }
        }
    }
}
