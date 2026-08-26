package com.ditto.domain.member.entity

import com.ditto.domain.BaseEntity
import com.ditto.domain.notification.entity.NotificationCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

/**
 * 설정 화면(피그마 6.2)의 알림 토글 3종.
 *
 * 행이 없으면 [defaultOf]가 주는 기본값으로 간주한다 — 가입 시 미리 만들지 않고,
 * 회원이 토글을 처음 건드릴 때 생성된다. 그래서 조회는 항상 성공하며 404가 없다.
 *
 * 발송 인프라는 아직 없다. 이 값은 저장·조회만 되며, 발송이 붙을 때 게이트로 쓰인다.
 */
@Entity
@Table(
    name = "member_notification_setting",
    uniqueConstraints = [
        UniqueConstraint(name = "member_notification_setting_uk_1", columnNames = ["member_id"]),
    ],
)
class MemberNotificationSetting(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("매칭 알림 수신 여부")
    @Column(nullable = false)
    var matching: Boolean = true,

    @Comment("채팅 알림 수신 여부")
    @Column(nullable = false)
    var chat: Boolean = true,

    @Comment("마케팅 정보 수신 여부")
    @Column(nullable = false)
    var marketing: Boolean = false,
) : BaseEntity() {

    /**
     * 이 카테고리의 푸시를 받겠다고 했는지 — 발송 게이트. 인앱 알림 센터는 이 값과 무관하게 쌓인다.
     *
     * SYSTEM(운영 공지)은 막지 않는다 — 설정 화면(피그마 6.2)의 세 토글 중 어디에도 속하지 않는다.
     * `marketing` 토글은 마케팅 수신 동의라 공지와 다른 개념이다. 어드민 공지(발송 주체)가 생길 때
     * 공지 성격에 따라 재검토한다.
     */
    fun allowsPush(category: NotificationCategory): Boolean = when (category) {
        NotificationCategory.MATCHING -> matching
        NotificationCategory.CHAT -> chat
        NotificationCategory.SYSTEM -> true
    }

    /** 부분 패치 — null인 항목은 변경하지 않는다. */
    fun update(matching: Boolean?, chat: Boolean?, marketing: Boolean?) {
        if (matching != null) this.matching = matching
        if (chat != null) this.chat = chat
        if (marketing != null) this.marketing = marketing
    }

    companion object {
        /**
         * 미설정 회원의 기본값. 매칭·채팅은 서비스 이용에 필요한 알림이라 수신,
         * 마케팅은 별도 동의 대상이라 미수신으로 둔다.
         */
        fun defaultOf(memberId: Long): MemberNotificationSetting = MemberNotificationSetting(
            memberId = memberId,
            matching = true,
            chat = true,
            marketing = false,
        )
    }
}
