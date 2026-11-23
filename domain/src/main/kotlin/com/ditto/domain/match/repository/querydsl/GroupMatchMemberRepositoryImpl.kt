package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.entity.QGroupMatch.groupMatch
import com.ditto.domain.match.entity.QGroupMatchMember
import com.ditto.domain.match.entity.QGroupMatchMember.groupMatchMember
import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.member.repository.querydsl.matchesFilter
import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.LocalDate
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class GroupMatchMemberRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : GroupMatchMemberRepositoryCustom {

    override fun existsByMemberIdAndQuizSetId(memberId: Long, quizSetId: Long): Boolean = queryFactory
        .selectOne()
        .from(groupMatchMember)
        .join(groupMatch).on(groupMatchMember.roomId.eq(groupMatch.id))
        .where(
            groupMatchMember.memberId.eq(memberId),
            groupMatch.quizSetId.eq(quizSetId),
        )
        .fetchFirst() != null

    override fun existsAcceptedInUnformedGroupOfWeek(memberId: Long, weekStartedOn: LocalDate): Boolean =
        queryFactory
            .selectOne()
            .from(groupMatchMember)
            .join(groupMatch).on(groupMatchMember.roomId.eq(groupMatch.id))
            .join(quizSet).on(groupMatch.quizSetId.eq(quizSet.id))
            .where(
                groupMatchMember.memberId.eq(memberId),
                groupMatchMember.status.eq(InvitationStatus.ACCEPTED),
                groupMatch.isActive.isFalse,
                quizSet.weekStartedOn.eq(weekStartedOn),
            )
            .fetchFirst() != null

    override fun existsAcceptedInFormedGroupOfWeek(memberId: Long, weekStartedOn: LocalDate): Boolean =
        queryFactory
            .selectOne()
            .from(groupMatchMember)
            .join(groupMatch).on(groupMatchMember.roomId.eq(groupMatch.id))
            .join(quizSet).on(groupMatch.quizSetId.eq(quizSet.id))
            .where(
                groupMatchMember.memberId.eq(memberId),
                groupMatchMember.status.eq(InvitationStatus.ACCEPTED),
                groupMatch.isActive.isTrue,
                quizSet.weekStartedOn.eq(weekStartedOn),
            )
            .fetchFirst() != null

    /**
     * 같은 그룹 채팅방에 **함께 들어간** 사이인가.
     *
     * 배치가 후보 그룹의 구성원을 PENDING 으로 미리 깔기 때문에, 같은 `room_id` 를 공유한다는 것만으로는
     * 부족하다. 그것만 보면 알고리즘이 같은 후보에 넣기만 해도 서로의 프로필·소개노트가 열린다
     * (`MatchAccessChecker` → `UserService`·`IntroNoteService`).
     * 양쪽 모두 수락했고 그룹이 성사된 경우만 참으로 본다.
     */
    override fun existsSharedRoom(memberId: Long, otherMemberId: Long): Boolean {
        val other = QGroupMatchMember("other")
        return queryFactory
            .selectOne()
            .from(groupMatchMember)
            .join(other).on(groupMatchMember.roomId.eq(other.roomId))
            .join(groupMatch).on(groupMatchMember.roomId.eq(groupMatch.id))
            .where(
                groupMatchMember.memberId.eq(memberId),
                other.memberId.eq(otherMemberId),
                groupMatchMember.status.eq(InvitationStatus.ACCEPTED),
                other.status.eq(InvitationStatus.ACCEPTED),
                groupMatch.isActive.isTrue,
            )
            .fetchFirst() != null
    }

    override fun existsSharedCandidateGroup(memberId: Long, otherMemberId: Long, quizSetId: Long): Boolean {
        val other = QGroupMatchMember("other")
        return queryFactory
            .selectOne()
            .from(groupMatchMember)
            .join(other).on(groupMatchMember.roomId.eq(other.roomId))
            .join(groupMatch).on(groupMatchMember.roomId.eq(groupMatch.id))
            .where(
                groupMatchMember.memberId.eq(memberId),
                other.memberId.eq(otherMemberId),
                groupMatch.quizSetId.eq(quizSetId),
                groupMatchMember.status.ne(InvitationStatus.DECLINED),
                other.status.ne(InvitationStatus.DECLINED),
            )
            .fetchFirst() != null
    }

    override fun countAcceptedMembersPerActiveGroupInWeek(weekStartedOn: LocalDate): List<ActiveGroupMemberCount> {
        val countExpression = groupMatchMember.count()
        return queryFactory
            .select(groupMatch.id, member.gender, countExpression)
            .from(groupMatchMember)
            .join(groupMatch).on(groupMatchMember.roomId.eq(groupMatch.id), groupMatch.isActive.isTrue)
            .join(quizSet).on(groupMatch.quizSetId.eq(quizSet.id), quizSet.isActive.isTrue)
            .join(member).on(member.id.eq(groupMatchMember.memberId))
            .where(
                quizSet.weekStartedOn.eq(weekStartedOn),
                groupMatchMember.status.eq(InvitationStatus.ACCEPTED),
            )
            .groupBy(groupMatch.id, member.gender)
            .fetch()
            .map { row ->
                ActiveGroupMemberCount(
                    groupMatchId = row.get(groupMatch.id) ?: throw IllegalStateException("group by 키 id 가 비었다"),
                    gender = row.get(member.gender),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    override fun countInvitedMembersInWeek(
        weekStartedOn: LocalDate,
        filter: MemberFilter,
    ): List<GroupMatchMemberCount> {
        val countExpression = groupMatchMember.count()
        return queryFactory
            .select(groupMatch.isActive, groupMatchMember.status, member.gender, countExpression)
            .from(groupMatchMember)
            .join(groupMatch).on(groupMatchMember.roomId.eq(groupMatch.id))
            .join(quizSet).on(groupMatch.quizSetId.eq(quizSet.id), quizSet.isActive.isTrue)
            .join(member).on(member.id.eq(groupMatchMember.memberId))
            .where(
                quizSet.weekStartedOn.eq(weekStartedOn),
                member.matchesFilter(filter),
            )
            .groupBy(groupMatch.isActive, groupMatchMember.status, member.gender)
            .fetch()
            .map { row ->
                GroupMatchMemberCount(
                    isGroupActive = row.get(groupMatch.isActive)
                        ?: throw IllegalStateException("group by 키 isActive 가 비었다"),
                    status = row.get(groupMatchMember.status)
                        ?: throw IllegalStateException("group by 키 status 가 비었다"),
                    gender = row.get(member.gender),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }
}
