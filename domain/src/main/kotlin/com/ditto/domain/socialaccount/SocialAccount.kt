package com.ditto.domain.socialaccount

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

@Entity
@Table(
    name = "social_account",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["provider", "provider_user_id"]),
    ],
)
class SocialAccount private constructor(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: SocialProvider,

    @Column(name = "provider_user_id", nullable = false)
    val providerUserId: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
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
