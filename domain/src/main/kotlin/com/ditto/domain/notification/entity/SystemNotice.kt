package com.ditto.domain.notification.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

/**
 * 어드민이 보낸 시스템 공지 한 건. 수신자별 기록은 [Notification](SYSTEM_NOTICE, target_id = 이 id)이고,
 * 여기는 "언제 누가 무엇을 몇 명에게 보냈나"만 남긴다. notification 은 30일 뒤 지워지지만 이 행은 남는다.
 *
 * 문구 길이 제한은 [Notification]과 같다. 공지 문구가 그대로 알림 문구가 되기 때문이다.
 */
@Entity
@Table(name = "system_notice")
class SystemNotice private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("제목")
    @Column(nullable = false, length = Notification.TITLE_MAX_LENGTH)
    val title: String,

    @Comment("본문")
    @Column(length = Notification.BODY_MAX_LENGTH)
    val body: String?,

    @Comment("발송한 어드민 회원 ID")
    @Column(name = "author_member_id", nullable = false)
    val authorMemberId: Long,

    @Comment("발송자 이름 (발송 시점 스냅샷)")
    @Column(name = "author_name", length = 50)
    val authorName: String?,

    @Comment("발송자 이메일 (발송 시점 스냅샷)")
    @Column(name = "author_email", length = 100)
    val authorEmail: String?,
) : BaseEntity() {

    @Comment("적재된 알림 수")
    @Column(name = "recipient_count", nullable = false)
    var recipientCount: Int = 0
        protected set

    /** 발송이 끝난 뒤 실제로 적재된 수를 남긴다. */
    fun recordRecipientCount(count: Int) {
        recipientCount = count
    }

    companion object {
        fun create(
            title: String,
            body: String?,
            authorMemberId: Long,
            authorName: String?,
            authorEmail: String?,
        ): SystemNotice = SystemNotice(
            title = title,
            body = body,
            authorMemberId = authorMemberId,
            authorName = authorName,
            authorEmail = authorEmail,
        )
    }
}
