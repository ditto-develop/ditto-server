package com.ditto.domain.member.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.BaseEntity
import com.ditto.domain.member.converter.InterestSetConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
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
import java.time.LocalDateTime

@Entity
@Table(
    name = "member",
    indexes = [
        Index(name = "member_index_1", columnList = "created_at, status"),
        Index(name = "member_index_2", columnList = "status, suspended_until"),
    ],
    uniqueConstraints = [UniqueConstraint(name = "member_unique_1", columnNames = ["nickname"])],
)
class Member(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("닉네임")
    @Column(nullable = false, length = 50)
    var nickname: String,

    @Comment("이메일")
    @Column(nullable = true, length = 100)
    var email: String? = null,

    @Comment("회원 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MemberStatus = MemberStatus.PENDING,

    @Comment("이용 정지 해제 예정 일시 (SUSPENDED일 때만 값 존재)")
    @Column(name = "suspended_until", nullable = true)
    var suspendedUntil: LocalDateTime? = null,

    @Comment("회원 권한")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: MemberRole = MemberRole.USER,

    @Comment("이름")
    @Column(nullable = true, length = 50)
    var name: String? = null,

    @Comment("전화번호")
    @Column(name = "phone_number", nullable = true, length = 20)
    var phoneNumber: String? = null,

    @Comment("성별")
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 10)
    var gender: Gender? = null,

    @Comment("나이대")
    @Column(nullable = true)
    var age: Int? = null,

    @Comment("생년월일")
    @Column(name = "birth_date", nullable = true)
    var birthDate: LocalDateTime? = null,

    @Comment("가입일시")
    @Column(name = "joined_at", nullable = true)
    var joinedAt: LocalDateTime? = null,

    @Comment("관심사 (콤마 구분 enum 문자열)")
    @Convert(converter = InterestSetConverter::class)
    @Column(nullable = false, length = 500)
    var interests: Set<Interest> = emptySet(),

    @Comment("사는곳")
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    var location: Location? = null,

    @Comment("직업")
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 30)
    var job: Job? = null,

    @Comment("프로필 캐리커쳐 (FE에서 받은 문자열 그대로 저장)")
    @Column(nullable = true, length = 100)
    var caricature: String? = null,
) : BaseEntity() {

    fun activate() {
        status = MemberStatus.ACTIVE
    }

    fun hasEmailChanged(email: String?): Boolean = email != null && this.email != email

    fun updateEmail(email: String?) {
        if (email != null) {
            this.email = email
        }
    }

    /**
     * 소셜 로그인에서 받은 개인정보로 갱신한다.
     * 제공된(non-null) 값만 덮어쓰며, 미동의로 null이 온 항목은 기존 값을 유지한다.
     */
    fun updateOAuthInfo(
        name: String?,
        phoneNumber: String?,
        gender: Gender?,
    ) {
        if (name != null) this.name = name
        if (phoneNumber != null) this.phoneNumber = phoneNumber
        if (gender != null) this.gender = gender
    }

    fun isPending(): Boolean = status == MemberStatus.PENDING
    fun isActive(): Boolean = status == MemberStatus.ACTIVE
    fun isAdmin(): Boolean = role == MemberRole.ADMIN
    fun isBanned(): Boolean = status == MemberStatus.BANNED

    /**
     * 주어진 시각 기준 이용 정지 중인지. 해제 예정일이 지났으면 정지로 보지 않는다
     * — status 원복은 배치·로그인 시점에 일어난다 (lazy 만료, ADR 0009).
     */
    fun isSuspendedAt(now: LocalDateTime): Boolean {
        if (status != MemberStatus.SUSPENDED) return false
        val until = suspendedUntil ?: return true
        return until > now
    }

    /** 회원 권한을 변경한다(어드민 운영용). */
    fun changeRole(role: MemberRole) {
        this.role = role
    }

    /** 기간 이용 정지. 영구 차단(BANNED)은 정지로 낮출 수 없다. */
    fun suspendUntil(until: LocalDateTime) {
        if (status == MemberStatus.BANNED) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        status = MemberStatus.SUSPENDED
        suspendedUntil = until
    }

    /** 영구 차단. ACTIVE·SUSPENDED에서만 전이하며, 해제는 [reinstate](어드민 직권)로만 가능하다. */
    fun ban() {
        if (status != MemberStatus.ACTIVE && status != MemberStatus.SUSPENDED) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        status = MemberStatus.BANNED
        suspendedUntil = null
    }

    /** 제재 해제 — 정지 만료·어드민 직권 해제 시 활성으로 원복한다. */
    fun reinstate() {
        if (status != MemberStatus.SUSPENDED && status != MemberStatus.BANNED) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        status = MemberStatus.ACTIVE
        suspendedUntil = null
    }

    fun register(
        name: String?,
        nickname: String?,
        phoneNumber: String?,
        gender: Gender?,
        age: Int?,
        birthDate: LocalDateTime?,
        email: String?,
        interests: Set<Interest>,
        location: Location,
        job: Job,
        caricature: String,
    ) {
        // 가입 완료는 PENDING에서만 — 제재(SUSPENDED/BANNED) 회원이 이 경로로 ACTIVE가 되는 것을 봉쇄한다.
        if (status != MemberStatus.PENDING) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        if (name != null) this.name = name
        if (nickname != null) this.nickname = nickname
        if (phoneNumber != null) this.phoneNumber = phoneNumber
        if (gender != null) this.gender = gender
        if (age != null) this.age = age
        if (birthDate != null) this.birthDate = birthDate
        if (email != null) this.email = email
        // 관심사·사는곳·직업·캐리커쳐는 가입 완료 시 필수값이므로 항상 채운다.
        this.interests = interests
        this.location = location
        this.job = job
        this.caricature = caricature
        this.joinedAt = LocalDateTime.now()
        this.status = MemberStatus.ACTIVE
    }
}
