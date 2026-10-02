package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.entity.QGroupMatch.groupMatch
import com.ditto.domain.match.entity.QGroupMatchMember
import com.ditto.domain.match.entity.QGroupMatchMember.groupMatchMember
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.LocalDateTime
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class GroupMatchMemberRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : GroupMatchMemberRepositoryCustom {

    @Transactional
    override fun declinePendingInvitations(groupMatchIds: Collection<Long>, updatedAt: LocalDateTime): Long {
        if (groupMatchIds.isEmpty()) return 0
        // 매분 도는 배치라 바꿀 행이 없을 때는 UPDATE 를 내지 않는다. UPDATE 는 조건에 안 맞는 행까지 잠근다.
        val pendingIds = queryFactory
            .select(groupMatchMember.id)
            .from(groupMatchMember)
            .where(
                groupMatchMember.roomId.`in`(groupMatchIds),
                groupMatchMember.status.eq(InvitationStatus.PENDING),
            )
            .fetch()
        if (pendingIds.isEmpty()) return 0

        return queryFactory
            .update(groupMatchMember)
            .set(groupMatchMember.status, InvitationStatus.DECLINED)
            .set(groupMatchMember.updatedAt, updatedAt)
            .where(
                groupMatchMember.id.`in`(pendingIds),
                groupMatchMember.status.eq(InvitationStatus.PENDING),
            )
            .execute()
    }

    override fun existsByMemberIdAndQuizSetId(memberId: Long, quizSetId: Long): Boolean = queryFactory
        .selectOne()
        .from(groupMatchMember)
        .join(groupMatch).on(groupMatchMember.roomId.eq(groupMatch.id))
        .where(
            groupMatchMember.memberId.eq(memberId),
            groupMatch.quizSetId.eq(quizSetId),
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
}
