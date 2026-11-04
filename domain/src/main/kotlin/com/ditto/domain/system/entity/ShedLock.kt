package com.ditto.domain.system.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

/**
 * 스케줄러 중복 실행 방지 락(ShedLock) 테이블. 행은 ShedLock 이 JDBC 로 직접 읽고 쓰며 이 엔티티로 다루지 않는다.
 *
 * 엔티티로 두는 이유는 스키마를 JPA 가 알게 하려는 것이다 — 로컬·테스트(H2 `create-drop`)에 테이블이 생기고,
 * 운영(`validate`)에서 마이그레이션을 빠뜨리면 기동이 실패한다. 테이블이 없으면 ShedLock 은 락을 못 잡고
 * 스케줄러가 조용히 전부 건너뛰는데, 그보다 배포가 롤백되는 편이 낫다.
 *
 * 시각은 DB 시각(UTC) 기준이다(`usingDbTime`). 서버 시각·어드민 시각 오버라이드와 무관하다.
 */
@Entity
@Table(name = "shedlock")
class ShedLock private constructor(
    @Id
    @Comment("락 이름 (스케줄 작업 이름)")
    @Column(length = 64)
    val name: String,

    @Comment("락 만료 시각 (DB 시각, UTC)")
    @Column(name = "lock_until", nullable = false)
    val lockUntil: LocalDateTime,

    @Comment("락 획득 시각 (DB 시각, UTC)")
    @Column(name = "locked_at", nullable = false)
    val lockedAt: LocalDateTime,

    @Comment("락을 잡은 인스턴스 (호스트명)")
    @Column(name = "locked_by", nullable = false)
    val lockedBy: String,
)
