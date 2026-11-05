package com.ditto.domain.system

import com.ditto.domain.support.IntegrationTest
import com.ditto.domain.system.entity.FeatureFlag
import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class FeatureFlagTest(
    private val featureFlagRepository: FeatureFlagRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val firstOn = LocalDateTime.of(2026, 11, 6, 10, 0)

    "새 플래그는 꺼져 있고 켠 시각이 없다" {
        val flag = featureFlagRepository.save(FeatureFlag.of(FeatureFlagKey.QUIZ_CHAT))

        flag.enabled shouldBe false
        flag.enabledAt.shouldBeNull()
    }

    "켜면 켠 시각과 바꾼 어드민을 남긴다" {
        val flag = featureFlagRepository.save(FeatureFlag.of(FeatureFlagKey.QUIZ_CHAT))

        flag.enable(firstOn, adminMemberId = 7L)
        featureFlagRepository.saveAndFlush(flag)

        val saved = featureFlagRepository.findByKey(FeatureFlagKey.QUIZ_CHAT)!!
        saved.enabled shouldBe true
        saved.enabledAt shouldBe firstOn
        saved.updatedByMemberId shouldBe 7L
    }

    "이미 켜진 플래그를 다시 켜도 처음 켠 시각을 유지한다 — 그 사이 열린 방이 대상에서 빠지면 안 된다" {
        val flag = FeatureFlag.of(FeatureFlagKey.QUIZ_CHAT)
        flag.enable(firstOn, adminMemberId = 7L)

        flag.enable(firstOn.plusHours(3), adminMemberId = 8L)

        flag.enabledAt shouldBe firstOn
        flag.updatedByMemberId shouldBe 8L
    }

    "끄면 켠 시각을 지운다 — 다시 켜면 그때부터 새로 센다" {
        val flag = FeatureFlag.of(FeatureFlagKey.QUIZ_CHAT)
        flag.enable(firstOn, adminMemberId = 7L)

        flag.disable(adminMemberId = 9L)

        flag.enabled shouldBe false
        flag.enabledAt.shouldBeNull()
        flag.updatedByMemberId shouldBe 9L
    }
})
