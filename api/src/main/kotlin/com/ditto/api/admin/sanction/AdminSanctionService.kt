package com.ditto.api.admin.sanction

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.sanction.dto.MemberSanctionsView
import com.ditto.api.admin.sanction.dto.SanctionRow
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.entity.SanctionStatus
import com.ditto.domain.sanction.repository.SanctionRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.jvm.optionals.getOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 제재 적용·해제의 공용 로직 — 신고 검토와 직권 조치가 함께 쓴다.
 * sanction 생성·종결과 `Member.status` 반영은 항상 같은 트랜잭션으로 묶는다 (ADR 0009).
 */
@Service
class AdminSanctionService(
    private val memberRepository: MemberRepository,
    private val sanctionRepository: SanctionRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @Transactional(readOnly = true)
    fun memberSanctions(memberId: Long): MemberSanctionsView {
        val member = memberRepository.findById(memberId).getOrNull()
            ?: throw WarnException(ErrorCode.NOT_FOUND)

        return MemberSanctionsView(
            memberId = member.id,
            nickname = member.nickname,
            statusName = member.status.name,
            strikeCount = sanctionRepository.countStrikes(memberId),
            sanctions = sanctionRepository.findAllByMemberIdOrderByIdDesc(memberId).map { it.toRow() },
        )
    }

    /** 제재 적용 — sanction 생성 + member 전이 + refresh 전량 회수. */
    @Transactional
    fun impose(
        memberId: Long,
        level: SanctionLevel,
        origin: SanctionOrigin,
        admin: AdminPrincipal,
        now: LocalDateTime,
        memberReportId: Long? = null,
        note: String? = null,
    ): Sanction {
        val member = memberRepository.findById(memberId).getOrNull()
            ?: throw WarnException(ErrorCode.NOT_FOUND, "회원이 존재하지 않아 제재를 적용할 수 없습니다.")

        val (startsAt, endsAt) = sanctionPeriod(level, now)
        val sanction = sanctionRepository.save(
            Sanction.impose(
                memberId = member.id,
                origin = origin,
                level = level,
                startsAt = startsAt,
                endsAt = endsAt,
                createdBy = admin.memberId,
                creatorName = admin.displayName,
                memberReportId = memberReportId,
                note = note,
            ),
        )

        when (level) {
            // 1차(경고)는 계정 상태를 바꾸지 않는다 — 퀴즈 참여만 sanction 구간으로 차단.
            SanctionLevel.WARNING -> return sanction
            SanctionLevel.SUSPENSION -> member.suspendUntil(requireNotNull(endsAt))
            SanctionLevel.PERMANENT_BAN -> member.ban()
        }
        // "즉시 발효": refresh 전량 회수 + 필터의 매 요청 검사로 access token 잔존과 무관하게 차단된다.
        refreshTokenRepository.deleteAllByMemberId(member.id)
        return sanction
    }

    /** 직권 해제(오처리 정정) — 남은 유효 제재 기준으로 회원 상태를 재계산한다. */
    @Transactional
    fun lift(sanctionId: Long, now: LocalDateTime): Sanction {
        val sanction = sanctionRepository.findById(sanctionId).getOrNull()
            ?: throw WarnException(ErrorCode.NOT_FOUND)

        // 조건부 UPDATE가 이중 해제를 방어한다 — 0이면 이미 종결(만료·해제)된 제재.
        if (sanctionRepository.liftIfActive(sanctionId, now) == 0) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        recalculateMemberStatus(sanction.memberId, now)
        return sanction
    }

    /** 해제 후 남은 유효 제재 중 가장 무거운 것으로 회원 상태를 맞춘다 (경고는 상태와 무관). */
    private fun recalculateMemberStatus(memberId: Long, now: LocalDateTime) {
        val member = memberRepository.findById(memberId).getOrNull() ?: return
        if (member.status != MemberStatus.SUSPENDED && member.status != MemberStatus.BANNED) {
            return
        }

        val heaviest = sanctionRepository.findAllByMemberIdAndStatus(memberId, SanctionStatus.ACTIVE)
            .filter { it.isEffectiveAt(now) && it.level != SanctionLevel.WARNING }
            .maxByOrNull { it.level }

        member.reinstate()
        when (heaviest?.level) {
            SanctionLevel.SUSPENSION -> member.suspendUntil(requireNotNull(heaviest.endsAt))
            SanctionLevel.PERMANENT_BAN -> member.ban()
            else -> {}
        }
    }

    /**
     * 제재 기간 (결정 5).
     * - WARNING: 확정 시점 기준 차주 월요일 00:00부터 7일 — 일요일 23:59:59까지 차단과 동일(endsAt은 exclusive 비교).
     *   확정한 주의 잔여 참여는 허용된다.
     * - SUSPENSION: 확정 즉시부터 14일.
     * - PERMANENT_BAN: 종료 없음.
     */
    private fun sanctionPeriod(level: SanctionLevel, now: LocalDateTime): Pair<LocalDateTime, LocalDateTime?> {
        return when (level) {
            SanctionLevel.WARNING -> {
                val nextMonday = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay()
                nextMonday to nextMonday.plusDays(7)
            }
            SanctionLevel.SUSPENSION -> now to now.plusDays(SUSPENSION_DAYS)
            SanctionLevel.PERMANENT_BAN -> now to null
        }
    }

    private fun Sanction.toRow() = SanctionRow(
        id = id,
        levelDescription = level.description,
        originDescription = origin.description,
        statusDescription = status.description,
        startsAt = startsAt,
        endsAt = endsAt,
        creatorName = creatorName,
        note = note,
        liftable = status == SanctionStatus.ACTIVE,
    )

    companion object {
        // 기획: 2차 제재 = 2주간 서비스 이용 정지
        private const val SUSPENSION_DAYS = 14L
    }
}
