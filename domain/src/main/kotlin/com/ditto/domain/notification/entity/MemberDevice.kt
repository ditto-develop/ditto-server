package com.ditto.domain.notification.entity

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

/**
 * 푸시 주소록 — FCM 디바이스 토큰과 소유 회원. 회원 하나가 기기 여러 개를 가질 수 있다.
 *
 * 토큰은 기기 소속이라 로그아웃해도 남는다. 그래서 token 단독 유일키로 "한 토큰 = 한 회원"을
 * 강제하고, 같은 기기에서 다른 회원이 로그인하면 행을 새로 만들지 않고 [transferTo]로 소유자만
 * 갈아끼운다. 안 그러면 이전 회원의 알림이 남의 폰에 뜬다.
 *
 * 토큰 형식은 해석하지 않는다.
 */
@Entity
@Table(
    name = "member_device",
    uniqueConstraints = [
        UniqueConstraint(name = "member_device_uk_1", columnNames = ["token"]),
    ],
    indexes = [
        // 발송·탈퇴 정리가 회원의 기기 목록을 읽는 경로.
        Index(name = "member_device_index_1", columnList = "member_id"),
    ],
)
class MemberDevice(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    memberId: Long,

    @Comment("FCM 등록 토큰")
    @Column(nullable = false, length = TOKEN_MAX_LENGTH)
    val token: String,

    @Comment("기기 플랫폼")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val platform: DevicePlatform,
) : BaseEntity() {

    @Comment("소유 회원 ID")
    @Column(name = "member_id", nullable = false)
    var memberId: Long = memberId
        protected set

    fun isOwnedBy(memberId: Long): Boolean = this.memberId == memberId

    /** 소유자 갱신. 같은 회원이면 변경이 없어 재등록이 멱등해진다. */
    fun transferTo(memberId: Long) {
        this.memberId = memberId
    }

    companion object {
        const val TOKEN_MAX_LENGTH = 512
    }
}
