package com.ditto.domain.refreshtoken.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

@Entity
@Table(
    name = "refresh_token",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["token"]),
    ],
    indexes = [
        Index(name = "refresh_token_index_1", columnList = "member_id"),
    ],
)
class RefreshToken private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("리프레시 토큰 (UUID)")
    @Column(nullable = false, length = 36)
    val token: String,

    @Comment("만료 일시")
    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,
) : BaseEntity() {

    fun isExpired(now: LocalDateTime = LocalDateTime.now()): Boolean = expiresAt < now

    companion object {
        fun create(
            memberId: Long,
            token: String,
            expiresAt: LocalDateTime,
        ): RefreshToken = RefreshToken(
            memberId = memberId,
            token = token,
            expiresAt = expiresAt,
        )
    }
}
