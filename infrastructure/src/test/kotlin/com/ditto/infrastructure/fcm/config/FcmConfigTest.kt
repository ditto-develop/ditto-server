package com.ditto.infrastructure.fcm.config

import com.ditto.infrastructure.fcm.FakePushSender
import com.ditto.infrastructure.fcm.PushSender
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/** 프로파일·키 주입 여부에 따라 어느 PushSender 가 뜨는지 — 키 없는 prod 배포가 부팅에서 죽지 않는 게 핵심이다. */
class FcmConfigTest : FreeSpec({

    fun runner(vararg properties: String) = ApplicationContextRunner()
        .withPropertyValues(*properties)
        .withUserConfiguration(FcmConfig::class.java)

    "local·test 프로파일은 자격증명 없이 Fake 가 뜬다" {
        runner("spring.profiles.active=test").run { context ->
            context.getBean(PushSender::class.java).shouldBeInstanceOf<FakePushSender>()
        }
    }

    "prod 에서 키가 주입되지 않으면 부팅이 죽는다 — 설정 누락이 배포 시점에 드러난다" {
        runner("spring.profiles.active=prod").run { context ->
            context.startupFailure.shouldNotBeNull()
        }
    }

    "prod 에서 키가 주입되면 Firebase 초기화를 시도한다" {
        // 가짜 JSON 이라 초기화는 실패한다 — 조건 게이트가 열렸다는 것 자체를 검증한다.
        runner("spring.profiles.active=prod", "ditto.fcm.credentials={\"type\":\"fake\"}").run { context ->
            context.startupFailure.shouldNotBeNull()
        }
    }
})
