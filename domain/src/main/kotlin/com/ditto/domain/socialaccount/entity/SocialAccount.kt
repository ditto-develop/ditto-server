package com.ditto.domain.socialaccount.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

@Entity
@Table(
    name = "social_account",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["provider", "provider_user_id"]),
    ],
    indexes = [
        Index(name = "social_account_index_1", columnList = "member_id"),
    ],
)
class SocialAccount private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("소셜 로그인 제공자")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val provider: SocialProvider,

    @Comment("소셜 로그인 제공자의 사용자 고유 ID")
    @Column(name = "provider_user_id", nullable = false, length = 100)
    val providerUserId: String,
) : BaseEntity() {

    companion object {
        fun create(
            memberId: Long,
            provider: SocialProvider,
            providerUserId: String,
        ): SocialAccount = SocialAccount(
            memberId = memberId,
            provider = provider,
            providerUserId = providerUserId,
        )
    }
}
