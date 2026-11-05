package com.ditto.domain.system.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

/**
 * 서버 기능 플래그 한 개의 상태. 플래그마다 한 행이고, 행이 없으면 꺼져 있다.
 *
 * [enabledAt]은 **마지막으로 켠 시각**(어드민 화면 표시용)이다. 이미 켜져 있는데 다시 켜도 처음 켠 시각을 유지한다.
 * 켜기 전부터 진행 중이던 대상을 거르는 일은 시각 비교가 아니라 켜는 순간의 처리로 한다(ADR 0051).
 */
@Entity
@Table(
    name = "feature_flag",
    uniqueConstraints = [
        UniqueConstraint(name = "feature_flag_uk_1", columnNames = ["flag_key"]),
    ],
)
class FeatureFlag private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("플래그 키 (FeatureFlagKey)")
    @Enumerated(EnumType.STRING)
    @Column(name = "flag_key", nullable = false, length = 50)
    val key: FeatureFlagKey,
) : BaseEntity() {

    @Comment("켜짐 여부")
    @Column(nullable = false)
    var enabled: Boolean = false
        protected set

    @Comment("마지막으로 켠 시각 (꺼져 있으면 NULL)")
    @Column(name = "enabled_at")
    var enabledAt: LocalDateTime? = null
        protected set

    @Comment("마지막으로 바꾼 어드민 회원 ID")
    @Column(name = "updated_by_member_id")
    var updatedByMemberId: Long? = null
        protected set

    /** @return 꺼져 있다가 이번에 켜졌으면 true. 이미 켜져 있었으면 false(처음 켠 시각 유지). */
    fun enable(at: LocalDateTime, adminMemberId: Long): Boolean {
        updatedByMemberId = adminMemberId
        if (enabled) {
            return false
        }
        enabled = true
        enabledAt = at
        return true
    }

    fun disable(adminMemberId: Long) {
        updatedByMemberId = adminMemberId
        enabled = false
        enabledAt = null
    }

    companion object {
        fun of(key: FeatureFlagKey): FeatureFlag = FeatureFlag(key = key)
    }
}
