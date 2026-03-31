package com.ditto.domain.member.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Entity
@Table(name = "member")
class Member(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("닉네임")
    @Column(nullable = false, length = 50)
    var nickname: String,

    @Comment("회원 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MemberStatus = MemberStatus.PENDING,

) : BaseEntity() {

    fun activate() {
        status = MemberStatus.ACTIVE
    }

    fun isPending(): Boolean = status == MemberStatus.PENDING
}
