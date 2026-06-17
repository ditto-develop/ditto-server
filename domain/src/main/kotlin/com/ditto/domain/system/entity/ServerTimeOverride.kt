package com.ditto.domain.system.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

/**
 * 어드민이 설정한 서버 시각 오버라이드(단일 행 운영).
 *
 * [enabled] 가 true 이고 [overrideDateTime] 이 있으면 서버는 그 시각을 "현재 시각"으로 사용하고,
 * 비활성화(disable)하면 실제 시각을 사용한다. 설정 변경 시 변경자([authorName]/[authorEmail])를 함께 기록한다.
 */
@Entity
@Table(name = "server_time_override")
class ServerTimeOverride private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    enabled: Boolean,
    overrideDateTime: LocalDateTime?,
    authorName: String?,
    authorEmail: String?,
) : BaseEntity() {

    @Comment("오버라이드 활성화 여부")
    @Column(nullable = false)
    var enabled: Boolean = enabled
        protected set

    @Comment("오버라이드된 서버 시각")
    @Column(name = "override_date_time", nullable = true)
    var overrideDateTime: LocalDateTime? = overrideDateTime
        protected set

    @Comment("최종 설정자 이름")
    @Column(name = "author_name", nullable = true, length = 50)
    var authorName: String? = authorName
        protected set

    @Comment("최종 설정자 이메일")
    @Column(name = "author_email", nullable = true, length = 100)
    var authorEmail: String? = authorEmail
        protected set

    /** 서버 시각을 [dateTime] 으로 오버라이드하고 변경자를 기록한다. */
    fun override(dateTime: LocalDateTime, authorName: String?, authorEmail: String?) {
        this.enabled = true
        this.overrideDateTime = dateTime
        this.authorName = authorName
        this.authorEmail = authorEmail
    }

    /** 오버라이드를 해제한다(이후 실제 시각 사용). 설정 이력은 남긴다. */
    fun disable() {
        this.enabled = false
    }

    /** 현재 적용할 시각. 오버라이드가 활성이면 [overrideDateTime], 아니면 [fallback]. */
    fun resolve(fallback: LocalDateTime): LocalDateTime =
        overrideDateTime?.takeIf { enabled } ?: fallback

    companion object {
        /** 비활성(실제 시각 사용) 상태의 기본 인스턴스. 아직 저장 전이면 id=0. */
        fun disabled(): ServerTimeOverride = ServerTimeOverride(
            enabled = false,
            overrideDateTime = null,
            authorName = null,
            authorEmail = null,
        )
    }
}
