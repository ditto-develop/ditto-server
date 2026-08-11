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
import java.time.LocalDateTime
import org.hibernate.annotations.Comment

/**
 * 알림 센터(피그마 7.2)의 한 줄. **수신자 1명당 1행**이다 — 같은 사건이라도 받는 사람마다 문구가
 * 다르고(닉네임), 읽음도 사람마다 따로 관리된다.
 *
 * **문구를 만들어 저장한다.** 조회 때 다시 렌더하지 않는다. 채팅 SYSTEM 메시지는 코드만 남기고 문구를
 * 클라이언트가 만들지만(`docs/domains/chat.md`) 알림은 반대로 간다 — 곧 붙을 푸시와 센터가 같은 문구여야
 * 하고, 센터는 "그때 무엇을 알렸는가"의 기록이라 닉네임이 바뀐 뒤 다시 렌더하면 사실이 달라진다.
 *
 * 안읽음은 `readAt == null` 이다. 채팅처럼 읽음 커서 하나로 접지 않는다 — 화면이 개별 읽음을 요구한다.
 *
 * 보관은 30일이다([RETENTION_DAYS]). 화면이 최근 30일만 보여주므로 그 뒤의 행은 남길 이유가 없다.
 */
@Entity
@Table(
    name = "notification",
    indexes = [
        // 목록 조회 — 내 알림을 id DESC 로 커서 페이징한다.
        Index(name = "notification_index_1", columnList = "member_id, id"),
        // 미읽음 수(홈 배지)·전체 읽음이 읽는 경로.
        Index(name = "notification_index_2", columnList = "member_id, read_at"),
        // 같은 사건 중복 방지(존재 검사)와 새 메시지 알림 접기.
        Index(name = "notification_index_3", columnList = "member_id, type, target_id"),
    ],
)
class Notification private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("수신 회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("알림 유형")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Comment("제목 (발송 시점에 확정된 문구)")
    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    val title: String,

    @Comment("본문 (발송 시점에 확정된 문구)")
    @Column(length = BODY_MAX_LENGTH)
    val body: String?,

    @Comment("이동 대상 ID. 무엇을 가리키는지는 type 이 정한다")
    @Column(name = "target_id")
    val targetId: Long?,
) : BaseEntity() {

    @Comment("읽은 시각 (안읽음이면 NULL)")
    @Column(name = "read_at")
    var readAt: LocalDateTime? = null
        protected set

    val category: NotificationCategory
        get() = type.category

    val isRead: Boolean
        get() = readAt != null

    /**
     * 읽음으로 표시한다. 이미 읽은 알림이면 아무 것도 하지 않는다 —
     * 처음 읽은 시각을 덮어쓰지 않기 위한 것이고, 더블 탭·재시도가 멱등해진다.
     *
     * @return 이번 호출로 읽음이 됐으면 `true`
     */
    fun markRead(at: LocalDateTime): Boolean {
        if (isRead) {
            return false
        }
        readAt = at
        return true
    }

    companion object {
        const val TITLE_MAX_LENGTH = 100
        const val BODY_MAX_LENGTH = 500

        /** 목록 조회 창과 보관 기간. 화면 스펙("최근 30일")이 둘의 근거다. */
        const val RETENTION_DAYS = 30L

        /**
         * 긴 본문은 잘라서 저장한다. 본문이 메시지 미리보기인 경우가 있어(새 메시지 알림)
         * 길이를 부르는 쪽이 통제할 수 없다 — 여기서 자르지 않으면 컬럼 초과로 적재가 실패한다.
         */
        fun create(
            memberId: Long,
            type: NotificationType,
            title: String,
            body: String? = null,
            targetId: Long? = null,
        ): Notification = Notification(
            memberId = memberId,
            type = type,
            title = title.ellipsize(TITLE_MAX_LENGTH),
            body = body?.ellipsize(BODY_MAX_LENGTH),
            targetId = targetId,
        )

        private fun String.ellipsize(max: Int): String =
            if (length <= max) this else take(max - 1) + "…"
    }
}
