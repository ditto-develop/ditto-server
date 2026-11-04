package com.ditto.domain.system

import com.ditto.domain.support.IntegrationTest
import com.ditto.domain.system.entity.ShedLock
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import javax.sql.DataSource

class ShedLockMappingTest(
    private val entityManager: EntityManager,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "ShedLock 이 JDBC 로 쓴 행을 엔티티가 같은 컬럼으로 읽는다" {
        val lockUntil = LocalDateTime.of(2026, 11, 4, 0, 1)
        val lockedAt = LocalDateTime.of(2026, 11, 4, 0, 0)
        JdbcTemplate(dataSource).update(
            "INSERT INTO shedlock (name, lock_until, locked_at, locked_by) VALUES (?, ?, ?, ?)",
            "matching", lockUntil, lockedAt, "task-a",
        )

        val row = entityManager.find(ShedLock::class.java, "matching")

        row.lockUntil shouldBe lockUntil
        row.lockedAt shouldBe lockedAt
        row.lockedBy shouldBe "task-a"
    }
})
