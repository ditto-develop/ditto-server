package com.ditto.api.system.featureflag

import com.ditto.api.support.IntegrationTest
import com.ditto.domain.system.entity.FeatureFlag
import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.jdbc.core.JdbcTemplate
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
    private val dataSource: DataSource,
) : IntegrationTest(dataSource, {

    beforeTest { featureFlags.evict() }
    afterTest { featureFlags.evict() }

    /** 다른 서버가 DB 를 바꾼 상황 — 이 인스턴스 캐시는 모른다. */
    fun enableDirectlyInDb() {
        featureFlagRepository.save(FeatureFlag.of(FeatureFlagKey.SECRET_MATCHING).apply { enable(LocalDateTime.of(2026, 11, 6, 0, 0), 1L) })
    }

    "행이 없는 플래그는 꺼져 있다" {
        featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING) shouldBe false
    }

    "다른 서버가 바꾼 값은 캐시가 1분을 채우기 전까지 보이지 않고, 지나면 보인다" {
        var nanos = 0L
        val cache = FeatureFlags(featureFlagRepository) { nanos }
        cache.isEnabled(FeatureFlagKey.SECRET_MATCHING) shouldBe false
        enableDirectlyInDb()

        nanos += Duration.ofSeconds(59).toNanos()
        cache.isEnabled(FeatureFlagKey.SECRET_MATCHING) shouldBe false

        nanos += Duration.ofSeconds(1).toNanos()
        cache.isEnabled(FeatureFlagKey.SECRET_MATCHING) shouldBe true
    }

    "어드민이 이 서버에서 바꾸면 캐시를 기다리지 않고 곧바로 보인다" {
        featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING) shouldBe false

        featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = true, adminMemberId = 7L)
        featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING) shouldBe true
        featureFlagRepository.findByKey(FeatureFlagKey.SECRET_MATCHING)!!.enabledAt.shouldNotBeNull()

        featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = false, adminMemberId = 7L)
        featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING) shouldBe false
        featureFlagRepository.findByKey(FeatureFlagKey.SECRET_MATCHING)!!.enabledAt.shouldBeNull()
    }

    "어드민 목록은 정의된 플래그를 모두 주고, 행이 없으면 꺼진 것으로 보여 준다" {
        val views = featureFlagAdminService.list()

        views.map { it.key } shouldBe FeatureFlagKey.entries
        views.forEach {
            it.enabled shouldBe false
            it.updatedByMemberId.shouldBeNull()
        }
    }

    "이름으로 물으면 요청한 순서대로 답하고, 켠 것만 true, 모르는 이름은 false 다" {
        val states = featureFlags.statesOf(listOf("SECRET_MATCHING", "UNKNOWN"))
        states shouldBe mapOf("SECRET_MATCHING" to false, "UNKNOWN" to false)

        enableDirectlyInDb()
        featureFlags.evict()

        val after = featureFlags.statesOf(listOf("UNKNOWN", "SECRET_MATCHING"))
        after.keys.toList() shouldBe listOf("UNKNOWN", "SECRET_MATCHING")
        after shouldBe mapOf("UNKNOWN" to false, "SECRET_MATCHING" to true)
    }

    "공개를 마쳐 지운 키는 행이 없어도 true 다 — 옛 빌드 탭이 공개된 화면을 잃지 않게" {
        featureFlags.statesOf(listOf("RECORDS", "QUIZ_CHAT")) shouldBe mapOf("RECORDS" to true, "QUIZ_CHAT" to true)
    }

    "지운 키의 행이 DB 에 남아 있어도 조회와 어드민 목록이 실패하지 않는다" {
        val jdbc = JdbcTemplate(dataSource)
        // 운영 스키마는 VARCHAR 다(domain/db). 테스트 스키마는 엔티티에서 만들어 enum 값만 받을 수 있어 운영과 맞춘다.
        jdbc.execute("ALTER TABLE feature_flag ALTER COLUMN flag_key VARCHAR(50)")
        jdbc.update(
            "INSERT INTO feature_flag (flag_key, enabled, created_at, updated_at) VALUES ('QUIZ_CHAT', TRUE, NOW(), NOW())",
        )
        enableDirectlyInDb()

        featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING) shouldBe true
        featureFlagAdminService.list().map { it.key } shouldBe FeatureFlagKey.entries
    }

    "다시 켜도 처음 켠 시각을 유지한다" {
        featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = true, adminMemberId = 7L)
        val firstOn = featureFlagRepository.findByKey(FeatureFlagKey.SECRET_MATCHING)!!.enabledAt

        featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = true, adminMemberId = 8L)

        val row = featureFlagRepository.findByKey(FeatureFlagKey.SECRET_MATCHING)!!
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
                featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = true, adminMemberId = adminId)
            }
        }
        ready.await()
        go.countDown()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        featureFlagRepository.findAll().size shouldBe 1
        featureFlagRepository.findByKey(FeatureFlagKey.SECRET_MATCHING)!!.enabled shouldBe true
    }
})
