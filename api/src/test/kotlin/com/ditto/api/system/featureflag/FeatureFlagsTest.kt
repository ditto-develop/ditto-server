package com.ditto.api.system.featureflag

import com.ditto.api.support.IntegrationTest
import com.ditto.domain.system.entity.FeatureFlag
import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

class FeatureFlagsTest(
    private val featureFlags: FeatureFlags,
    private val featureFlagAdminService: FeatureFlagAdminService,
    private val featureFlagRepository: FeatureFlagRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    beforeTest { featureFlags.evict() }
    afterTest { featureFlags.evict() }

    /** 다른 서버가 DB 를 바꾼 상황 — 이 인스턴스 캐시는 모른다. */
    fun enableDirectlyInDb() {
        featureFlagRepository.save(FeatureFlag.of(FeatureFlagKey.QUIZ_CHAT).apply { enable(LocalDateTime.of(2026, 11, 6, 0, 0), 1L) })
    }

    "행이 없는 플래그는 꺼져 있다" {
        featureFlags.isEnabled(FeatureFlagKey.QUIZ_CHAT) shouldBe false
    }

    "다른 서버가 바꾼 값은 캐시가 1분을 채우기 전까지 보이지 않고, 지나면 보인다" {
        var nanos = 0L
        val cache = FeatureFlags(featureFlagRepository) { nanos }
        cache.isEnabled(FeatureFlagKey.QUIZ_CHAT) shouldBe false
        enableDirectlyInDb()

        nanos += Duration.ofSeconds(59).toNanos()
        cache.isEnabled(FeatureFlagKey.QUIZ_CHAT) shouldBe false

        nanos += Duration.ofSeconds(1).toNanos()
        cache.isEnabled(FeatureFlagKey.QUIZ_CHAT) shouldBe true
    }

    "어드민이 이 서버에서 바꾸면 캐시를 기다리지 않고 곧바로 보인다" {
        featureFlags.isEnabled(FeatureFlagKey.QUIZ_CHAT) shouldBe false

        featureFlagAdminService.set(FeatureFlagKey.QUIZ_CHAT, enabled = true, adminMemberId = 7L)
        featureFlags.isEnabled(FeatureFlagKey.QUIZ_CHAT) shouldBe true
        featureFlagRepository.findByKey(FeatureFlagKey.QUIZ_CHAT)!!.enabledAt.shouldNotBeNull()

        featureFlagAdminService.set(FeatureFlagKey.QUIZ_CHAT, enabled = false, adminMemberId = 7L)
        featureFlags.isEnabled(FeatureFlagKey.QUIZ_CHAT) shouldBe false
        featureFlagRepository.findByKey(FeatureFlagKey.QUIZ_CHAT)!!.enabledAt.shouldBeNull()
    }

    "어드민 목록은 정의된 플래그를 모두 주고, 행이 없으면 꺼진 것으로 보여 준다" {
        val views = featureFlagAdminService.list()

        views.map { it.key } shouldBe FeatureFlagKey.entries
        views.single().enabled shouldBe false
        views.single().updatedByMemberId.shouldBeNull()
    }

    "다시 켜도 처음 켠 시각을 유지한다" {
        featureFlagAdminService.set(FeatureFlagKey.QUIZ_CHAT, enabled = true, adminMemberId = 7L)
        val firstOn = featureFlagRepository.findByKey(FeatureFlagKey.QUIZ_CHAT)!!.enabledAt

        featureFlagAdminService.set(FeatureFlagKey.QUIZ_CHAT, enabled = true, adminMemberId = 8L)

        val row = featureFlagRepository.findByKey(FeatureFlagKey.QUIZ_CHAT)!!
        row.enabledAt shouldBe firstOn
        row.updatedByMemberId shouldBe 8L
    }

    "처음 켜기를 동시에 두 번 눌러도 행은 하나이고 둘 다 성공한다" {
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val futures = listOf(7L, 8L).map { adminId ->
            pool.submit {
                ready.countDown()
                go.await()
                featureFlagAdminService.set(FeatureFlagKey.QUIZ_CHAT, enabled = true, adminMemberId = adminId)
            }
        }
        ready.await()
        go.countDown()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        featureFlagRepository.findAll().size shouldBe 1
        featureFlagRepository.findByKey(FeatureFlagKey.QUIZ_CHAT)!!.enabled shouldBe true
    }
})
