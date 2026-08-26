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
 * 푸시 발송(FCM)의 주소록 한 줄 — 앱이 FCM 에서 받은 디바이스 토큰의 소유 회원.
 * 회원 1명이 여러 행을 가질 수 있다(폰·태블릿, 기기 교체).
 *
 * **토큰 단독 유일 제약이 "한 토큰 = 한 회원"을 DB 수준에서 강제한다.** 토큰은 기기의 것이라
 * 로그아웃해도 그대로다 — 공용 기기에서 다른 회원이 로그인하면 행을 새로 만들지 않고
 * [transferTo]로 소유자를 갱신해, 이전 회원의 알림이 남의 폰에 뜨는 것을 막는다.
 *
 * 토큰은 불투명 문자열이다. 형식이 보장되지 않으므로 잘라서 해석하거나 검증하지 않는다.
 */
@Entity
@Table(
    name = "member_device",
    uniqueConstraints = [
        UniqueConstraint(name = "member_device_uk_1", columnNames = ["token"]),
    ],
    indexes = [
        // 발송이 회원의 기기 목록을 집어오는 경로. 탈퇴 정리도 이 인덱스를 탄다.
        Index(name = "member_device_index_1", columnList = "member_id"),
    ],
)
class MemberDevice(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    memberId: Long,

    @Comment("FCM 등록 토큰 (불투명 문자열 — 형식을 해석하지 않는다)")
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

    /** 소유자를 갱신한다. 같은 회원이면 변경이 없어 재등록이 멱등해진다. */
    fun transferTo(memberId: Long) {
        this.memberId = memberId
    }

    companion object {
        const val TOKEN_MAX_LENGTH = 512
    }
}
