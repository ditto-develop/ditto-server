package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.intronote.entity.QIntroNote.introNote
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.member.entity.QNicknameReservation.nicknameReservation
import com.ditto.domain.notification.entity.QMemberDevice.memberDevice
import com.ditto.domain.quiz.entity.QQuizProgress.quizProgress
import com.ditto.domain.socialaccount.entity.QSocialAccount.socialAccount
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Transactional(readOnly = true)
class MemberRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : MemberRepositoryCustom {

    override fun findAllIdsByStatus(status: MemberStatus): List<Long> =
        queryFactory
            .select(member.id)
            .from(member)
            .where(member.status.eq(status))
            .fetch()

    override fun countMembersByStatus(filter: MemberFilter): Map<MemberStatus, Long> {
        val countExpression = member.count()
        return queryFactory
            .select(member.status, countExpression)
            .from(member)
            // 가입하지 않고 탈퇴(LEFT)된 회원도 있어서 상태가 아니라 joined_at 으로 가입자를 가른다.
            .where(
                member.status.eq(MemberStatus.PENDING).or(member.joinedAt.isNotNull),
                member.matchesFilter(filter),
            )
            .groupBy(member.status)
            .fetch()
            .associate { row ->
                val status = row.get(member.status) ?: throw IllegalStateException("group by 키 status 가 비었다")
                status to (row.get(countExpression) ?: 0L)
            }
    }

    override fun findAllJoinedAtsWithin(
        joinedAtRange: OpenEndRange<LocalDateTime>,
        filter: MemberFilter,
    ): List<LocalDateTime> =
        queryFactory
            .select(member.joinedAt)
            .from(member)
            .where(
                member.joinedAt.goe(joinedAtRange.start),
                member.joinedAt.lt(joinedAtRange.endExclusive),
                member.matchesFilter(filter),
            )
            .fetch()
            .filterNotNull()

    override fun countCurrentMembersByGenderAndBirthDate(filter: MemberFilter): List<GenderBirthDateCount> {
        val countExpression = member.count()
        return queryFactory
            .select(member.gender, member.birthDate, countExpression)
            .from(member)
            .where(member.joinedAt.isNotNull, member.status.ne(MemberStatus.LEFT), member.matchesFilter(filter))
            .groupBy(member.gender, member.birthDate)
            .fetch()
            .map { row ->
                GenderBirthDateCount(
                    gender = row.get(member.gender),
                    birthDate = row.get(member.birthDate)?.toLocalDate(),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    override fun countCurrentMembersByLocation(filter: MemberFilter): List<LocationCount> {
        val countExpression = member.count()
        return queryFactory
            .select(member.location, countExpression)
            .from(member)
            .where(member.joinedAt.isNotNull, member.status.ne(MemberStatus.LEFT), member.matchesFilter(filter))
            .groupBy(member.location)
            .fetch()
            .map { row ->
                LocationCount(location = row.get(member.location), count = row.get(countExpression) ?: 0L)
            }
    }

    override fun countMembersLeftWithinByLeaveReason(
        leftAtRange: OpenEndRange<LocalDateTime>,
        filter: MemberFilter,
    ): List<LeaveReasonCount> {
        val countExpression = member.count()
        return queryFactory
            .select(member.leaveReason, countExpression)
            .from(member)
            .where(
                member.joinedAt.isNotNull,
                member.leftAt.goe(leftAtRange.start),
                member.leftAt.lt(leftAtRange.endExclusive),
                member.matchesFilter(filter),
            )
            .groupBy(member.leaveReason)
            .fetch()
            .map { row ->
                LeaveReasonCount(leaveReason = row.get(member.leaveReason), count = row.get(countExpression) ?: 0L)
            }
    }

    override fun findSignupDropoutsStartedBefore(signupStartedBefore: LocalDateTime): List<SignupDropout> {
        val deviceCount = memberDevice.id.countDistinct()
        return queryFactory
            .select(member.id, member.createdAt, socialAccount.provider, nicknameReservation.id, deviceCount)
            .from(member)
            .leftJoin(socialAccount).on(socialAccount.memberId.eq(member.id))
            .leftJoin(nicknameReservation).on(nicknameReservation.memberId.eq(member.id))
            .leftJoin(memberDevice).on(memberDevice.memberId.eq(member.id))
            .where(
                member.status.eq(MemberStatus.PENDING),
                member.createdAt.lt(signupStartedBefore),
                member.matchesFilter(MemberFilter.NONE),
            )
            .groupBy(member.id, member.createdAt, socialAccount.provider, nicknameReservation.id)
            .orderBy(member.createdAt.desc())
            .fetch()
            .map { row ->
                SignupDropout(
                    memberId = row.get(member.id) ?: throw IllegalStateException("group by 키 member.id 가 비었다"),
                    signupStartedAt = row.get(member.createdAt)
                        ?: throw IllegalStateException("group by 키 member.createdAt 이 비었다"),
                    socialProvider = row.get(socialAccount.provider),
                    hasNicknameReservation = row.get(nicknameReservation.id) != null,
                    hasPushDevice = (row.get(deviceCount) ?: 0L) > 0L,
                )
            }
    }

    override fun countJoinedMembersBySocialProvider(excludedNicknamePrefix: String): List<SocialProviderCount> {
        val countExpression = member.count()
        return queryFactory
            .select(socialAccount.provider, countExpression)
            .from(member)
            .leftJoin(socialAccount).on(socialAccount.memberId.eq(member.id))
            .where(
                member.joinedAt.isNotNull,
                member.matchesFilter(MemberFilter.NONE),
                member.nickname.startsWith(excludedNicknamePrefix).not(),
            )
            .groupBy(socialAccount.provider)
            .fetch()
            .map { row ->
                SocialProviderCount(
                    socialProvider = row.get(socialAccount.provider),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    override fun countActiveMembersWithPushDevice(
        filter: MemberFilter,
        excludedNicknamePrefix: String,
    ): PushDeviceCoverage {
        val memberCount = member.id.countDistinct()
        val memberWithPushDeviceCount = memberDevice.memberId.countDistinct()
        val row = queryFactory
            .select(memberCount, memberWithPushDeviceCount)
            .from(member)
            .leftJoin(memberDevice).on(memberDevice.memberId.eq(member.id))
            .where(
                member.status.eq(MemberStatus.ACTIVE),
                member.matchesFilter(filter),
                member.nickname.startsWith(excludedNicknamePrefix).not(),
            )
            .fetchOne()
        return PushDeviceCoverage(
            memberCount = row?.get(memberCount) ?: 0L,
            memberWithPushDeviceCount = row?.get(memberWithPushDeviceCount) ?: 0L,
        )
    }

    override fun findActiveMembersWithIntroNotesUnder(
        minIntroNoteCount: Long,
        excludedNicknamePrefix: String,
    ): List<QuizBlockedMember> {
        val introNoteCount = introNote.id.countDistinct()
        val quizProgressCount = quizProgress.id.countDistinct()
        return queryFactory
            .select(member.id, member.nickname, member.joinedAt, introNoteCount, quizProgressCount)
            .from(member)
            .leftJoin(introNote).on(introNote.memberId.eq(member.id), introNote.answer.trim().isNotEmpty)
            .leftJoin(quizProgress).on(quizProgress.memberId.eq(member.id))
            .where(
                member.status.eq(MemberStatus.ACTIVE),
                member.matchesFilter(MemberFilter.NONE),
                member.nickname.startsWith(excludedNicknamePrefix).not(),
            )
            .groupBy(member.id, member.nickname, member.joinedAt)
            .having(introNoteCount.lt(minIntroNoteCount))
            .orderBy(member.joinedAt.desc())
            .fetch()
            .map { row ->
                QuizBlockedMember(
                    memberId = row.get(member.id) ?: throw IllegalStateException("group by 키 member.id 가 비었다"),
                    nickname = row.get(member.nickname)
                        ?: throw IllegalStateException("group by 키 member.nickname 이 비었다"),
                    joinedAt = row.get(member.joinedAt),
                    introNoteCount = row.get(introNoteCount) ?: 0L,
                    hasStartedQuiz = (row.get(quizProgressCount) ?: 0L) > 0L,
                )
            }
    }
}
