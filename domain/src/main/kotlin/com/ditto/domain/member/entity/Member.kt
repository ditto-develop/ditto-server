package com.ditto.domain.member.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.BaseEntity
import com.ditto.domain.member.converter.InterestSetConverter
import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionLevel
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

    @Comment("현재 창에서 닉네임을 바꾼 횟수")
    @Column(name = "nickname_change_count", nullable = false)
    var nicknameChangeCount: Int = 0,

    @Comment("닉네임 변경 잠금 해제 시각 (잠기지 않았으면 NULL)")
    @Column(name = "nickname_change_locked_until", nullable = true)
    var nicknameChangeLockedUntil: LocalDateTime? = null,

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

    @Comment("탈퇴 일시 (LEFT일 때만 값 존재)")
    @Column(name = "left_at", nullable = true)
    var leftAt: LocalDateTime? = null,

    @Comment("탈퇴 사유 code (탈퇴 화면 선택값)")
    @Column(name = "leave_reason", nullable = true, length = 50)
    var leaveReason: String? = null,

    // code 와 분리하는 이유: 한 컬럼에 선택지 code 와 자유 서술이 섞이면 사유 집계 때 파싱이 필요해진다.
    @Comment("탈퇴 사유 자유 입력 (선택, 최대 100자)")
    @Column(name = "leave_reason_detail", nullable = true, length = 100)
    var leaveReasonDetail: String? = null,
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
    /**
     * 소셜 로그인이 준 정보를 회원에 반영한다. 준 값만 덮어쓴다(미동의 항목은 null로 오므로 기존 값이 남는다).
     *
     * 일반 앱(비즈 앱 미전환)에서는 이름·전화번호·성별·생년월일이 모두 null로 오므로 아무것도 바뀌지 않는다.
     * 나중에 비즈 앱으로 전환해 동의항목을 열면, 회원은 **재로그인만으로** 이 값들을 채우게 된다.
     */
    fun updateOAuthInfo(
        name: String?,
        phoneNumber: String?,
        gender: Gender?,
        birthDate: LocalDateTime? = null,
    ) {
        if (name != null) this.name = name
        if (phoneNumber != null) this.phoneNumber = phoneNumber
        if (gender != null) this.gender = gender
        if (birthDate != null) this.birthDate = birthDate
    }

    /**
     * 회원이 직접 입력한 신원 정보를 채운다. 가입 때 못 받은 값을 나중에 보완하는 경로다 —
     * 일반 앱에서는 카카오가 이름·전화번호·이메일·생년월일을 주지 않으므로 이 경로가 유일한 입력구다.
     *
     * null인 항목은 건드리지 않는다(부분 갱신). 성별·나이는 여기서 다루지 않는다 —
     * 가입 필수값이고, 프로필 수정 화면에서도 변경 대상이 아니다(피그마 6.1.1).
     */
    fun updatePersonalInfo(
        name: String?,
        phoneNumber: String?,
        email: String?,
        birthDate: LocalDateTime?,
    ) {
        if (name != null) this.name = name
        if (phoneNumber != null) this.phoneNumber = phoneNumber
        if (email != null) this.email = email
        if (birthDate != null) this.birthDate = birthDate
    }

    fun isLeft(): Boolean = status == MemberStatus.LEFT

    /**
     * 탈퇴 처리(소프트 삭제). 데이터는 지우지 않고 상태만 [MemberStatus.LEFT]로 바꾼다 —
     * 30일 안에 재가입하면 [restore]로 되돌리고, 그 뒤에 배치가 완전 삭제한다.
     *
     * 제재 중에도 탈퇴할 수 있다. hard delete 시절에는 제재 이력과 재가입 식별 근거(SocialAccount)가
     * 함께 사라져 차단 우회 수단이 됐지만, 소프트 삭제는 둘을 모두 보존하므로 막을 이유가 없다.
     */
    fun leave(reason: String?, now: LocalDateTime, reasonDetail: String? = null) {
        if (status == MemberStatus.LEFT) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION, "이미 탈퇴한 회원입니다.")
        }
        status = MemberStatus.LEFT
        leftAt = now
        leaveReason = reason
        leaveReasonDetail = reasonDetail
    }

    /**
     * 탈퇴 복구. 재가입(소셜 로그인) 시 [leftAt]이 보존 기간 안이면 호출된다.
     *
     * 탈퇴 이전 상태를 따로 저장하지 않으므로 ACTIVE로 되돌린다 — 제재 상태는 `sanction` 도메인이
     * SSOT이고 로그인·배치가 다시 반영하므로 여기서 복원할 필요가 없다.
     */
    fun restore() {
        if (status != MemberStatus.LEFT) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        status = MemberStatus.ACTIVE
        leftAt = null
        leaveReason = null
        leaveReasonDetail = null
    }

    /** 탈퇴 후 [retentionDays]일이 지났는지 — 완전 삭제 대상 판정. */
    fun isRetentionExpiredAt(now: LocalDateTime, retentionDays: Long): Boolean {
        val leftAt = leftAt ?: return false
        return leftAt.plusDays(retentionDays) <= now
    }

    /**
     * 마이프로필에서 수정 가능한 항목을 갱신한다. null이 온 항목은 변경 없음으로 둔다.
     *
     * 관심사는 온보딩 필수 정보라 빈 집합으로 지울 수 없다 — `register`가 세운 불변식을 유지한다.
     * 닉네임은 횟수 제한이 있어 [changeNickname]으로 따로 바꾼다.
     */
    fun updateProfile(changes: ProfileChanges) {
        changes.interests?.let { interests ->
            if (interests.isEmpty()) {
                throw WarnException(ErrorCode.BAD_REQUEST, "관심사는 최소 1개 이상이어야 합니다.")
            }
            this.interests = interests
        }
        // 빈 문자열도 거른다 — null 만 거르면 ""가 저장돼 필수값 불변식이 깨진다.
        if (!changes.caricature.isNullOrBlank()) {
            this.caricature = changes.caricature
        }
        changes.gender?.let { this.gender = it }
        changes.location?.let { this.location = it }
        changes.job?.let { this.job = it }
    }

    /**
     * 프로필 수정에서 닉네임을 바꾼다. 가입 때 정한 닉네임([register])은 세지 않는다.
     *
     * [NICKNAME_CHANGE_LIMIT]회 바꾸면 [NICKNAME_CHANGE_LOCK_DAYS]일 동안 잠기고, 잠금이 풀리면 횟수가 초기화된다.
     * 지금과 같은 값은 변경이 아니다 — 저장도, 횟수 차감도 하지 않는다. 대소문자만 바꾸는 것은 화면에 보이는
     * 값이 달라지므로 변경으로 센다. 중복·예약·채팅방 검사는 저장소를 봐야 해서 호출자가 먼저 한다.
     */
    fun changeNickname(nickname: String, now: LocalDateTime) {
        if (nickname == this.nickname) {
            return
        }
        val lockedUntil = nicknameChangeLockedUntil
        if (lockedUntil != null) {
            if (now < lockedUntil) {
                throw WarnException(ErrorCode.NICKNAME_CHANGE_LOCKED)
            }
            nicknameChangeCount = 0
            nicknameChangeLockedUntil = null
        }
        this.nickname = nickname
        nicknameChangeCount += 1
        if (nicknameChangeCount >= NICKNAME_CHANGE_LIMIT) {
            nicknameChangeLockedUntil = now.plusDays(NICKNAME_CHANGE_LOCK_DAYS)
        }
    }

    /** [now] 시점에 남은 닉네임 변경 횟수. 잠겨 있으면 0, 잠금이 풀렸으면 한도 전체다. */
    fun remainingNicknameChanges(now: LocalDateTime): Int {
        val lockedUntil = nicknameChangeLockedUntil ?: return NICKNAME_CHANGE_LIMIT - nicknameChangeCount
        return if (now < lockedUntil) 0 else NICKNAME_CHANGE_LIMIT
    }

    /** [now] 시점에 유효한 잠금 해제 시각. 잠겨 있지 않으면 null. */
    fun nicknameChangeLockedUntilAt(now: LocalDateTime): LocalDateTime? =
        nicknameChangeLockedUntil?.takeIf { now < it }

    fun isPending(): Boolean = status == MemberStatus.PENDING
    fun isActive(): Boolean = status == MemberStatus.ACTIVE
    fun isAdmin(): Boolean = role == MemberRole.ADMIN
    fun isBanned(): Boolean = status == MemberStatus.BANNED
    fun isRestricted(): Boolean = status == MemberStatus.SUSPENDED || status == MemberStatus.BANNED

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
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION, "영구 차단 중인 회원은 정지할 수 없습니다. 차단을 먼저 해제하세요.")
        }
        status = MemberStatus.SUSPENDED
        suspendedUntil = until
    }

    /** 영구 차단. ACTIVE·SUSPENDED에서만 전이하며, 해제는 [reinstate](어드민 직권)로만 가능하다. */
    fun ban() {
        if (status == MemberStatus.BANNED) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION, "이미 영구 차단된 회원입니다.")
        }
        if (status != MemberStatus.ACTIVE && status != MemberStatus.SUSPENDED) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        status = MemberStatus.BANNED
        suspendedUntil = null
    }

    /**
     * 제재를 해제하거나 지운 뒤, 남은 제재 중 지금 유효한 가장 무거운 것으로 상태를 맞춘다. 경고는 상태와 무관하다.
     * 제재가 줄어든 뒤에만 부르므로 정지·차단 중이 아니면 그대로 둔다.
     */
    fun alignStatusWith(sanctions: List<Sanction>, now: LocalDateTime) {
        if (!isRestricted()) return

        val heaviest = sanctions
            .filter { it.memberId == id && it.isEffectiveAt(now) && it.level != SanctionLevel.WARNING }
            .maxByOrNull { it.level }
        when (heaviest?.level) {
            SanctionLevel.PERMANENT_BAN -> {
                status = MemberStatus.BANNED
                suspendedUntil = null
            }
            SanctionLevel.SUSPENSION -> {
                status = MemberStatus.SUSPENDED
                suspendedUntil = requireNotNull(heaviest.endsAt) { "이용 정지 제재 #${heaviest.id}에 종료 시각이 없습니다." }
            }
            else -> {
                status = MemberStatus.ACTIVE
                suspendedUntil = null
            }
        }
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

    companion object {
        const val NICKNAME_CHANGE_LIMIT = 2
        const val NICKNAME_CHANGE_LOCK_DAYS = 14L
    }
}
