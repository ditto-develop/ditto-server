package com.ditto.infrastructure.fcm.config

import com.ditto.infrastructure.fcm.FakePushSender
import com.ditto.infrastructure.fcm.PushSender
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/** 프로파일·키 주입에 따라 어느 PushSender 가 뜨는지. 설정 누락은 부팅 실패로 드러나야 한다. */
class FcmConfigTest : FreeSpec({

    fun runner(vararg properties: String) = ApplicationContextRunner()
        .withPropertyValues(*properties)
        .withUserConfiguration(FcmConfig::class.java)

    "local·test 프로파일은 자격증명 없이 Fake 가 뜬다" - {
        listOf("local", "test").forEach { profile ->
            profile {
                runner("spring.profiles.active=$profile").run { context ->
                    context.getBean(PushSender::class.java).shouldBeInstanceOf<FakePushSender>()
                }
            }
        }
    }

    fun causeChainOf(failure: Throwable): List<Throwable> = generateSequence(failure) { it.cause }.toList()

    "prod 에서 키가 주입되지 않으면 부팅이 죽는다 — 프로퍼티 바인딩 단계에서" {
        runner("spring.profiles.active=prod").run { context ->
            val causes = causeChainOf(context.startupFailure.shouldNotBeNull())
            causes.any { it is ConfigurationPropertiesBindException } shouldBe true
        }
    }

    "prod 에서 키가 주입되면 자격증명 파싱까지 간다 — 게이트가 아니라 가짜 키가 실패 원인이다" {
        runner("spring.profiles.active=prod", "ditto.fcm.credentials={\"type\":\"fake\"}").run { context ->
            val causes = causeChainOf(context.startupFailure.shouldNotBeNull())
            causes.none { it is ConfigurationPropertiesBindException } shouldBe true
            causes.any { it is IOException } shouldBe true
        }
    }
})
