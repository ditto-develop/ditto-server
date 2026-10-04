package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaConsoleView
import com.ditto.api.admin.qa.dto.QaGroupMatch
import com.ditto.api.admin.qa.dto.QaGroupMember
import com.ditto.api.admin.qa.dto.QaGroupSection
import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.api.admin.qa.dto.QaPersonalRequestOption
import com.ditto.api.admin.qa.dto.QaPersonalSection
import com.ditto.api.admin.qa.dto.QaReceivedPersonalRequest
import com.ditto.api.admin.qa.dto.QaSentPersonalRequest
import com.ditto.api.admin.qa.dto.QaTimeShortcutOption
import com.ditto.api.match.GroupResponseDeadline
import com.ditto.api.system.ServerTimeProvider
import com.ditto.api.system.ServerTimeService
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** QA 콘솔의 1:1·그룹 응답 화면 조회. 더미가 실제로 움직이는 쓰기는 [AdminQaController]가 맡는다. */
@Service
@Transactional(readOnly = true)
class AdminQaService(
    private val qaDummies: QaDummies,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val serverTimeProvider: ServerTimeProvider,
    private val serverTimeService: ServerTimeService,
) {
    /** 시각은 한 번만 읽는다. 운영 주와 그룹 응답 마감이 같은 순간을 기준으로 해야 화면 안에서 어긋나지 않는다. */
    fun getConsole(): QaConsoleView {
        val now = serverTimeProvider.now()
        val week = OperationWeek.containing(now.toLocalDate())
        val dummyIds = qaDummies.findIds()
        val (personalQuizSets, groupQuizSets) = quizSetRepository.findByWeekStartedOn(week.startedOn)
            .partition { it.matchingType == MatchingType.ONE_TO_ONE }
        return QaConsoleView(
            now = now,
            isTimeOverridden = serverTimeService.getOverride().enabled,
            weekStartedOn = week.startedOn,
            dummyCount = dummyIds.size,
            personal = composePersonalSection(personalQuizSets, dummyIds),
            group = QaGroupSection(
                groups = composeGroups(groupQuizSets, dummyIds),
                isResponseClosed = GroupResponseDeadline.hasPassed(week, now),
            ),
            timeShortcuts = QaTimeShortcut.entries.map {
                QaTimeShortcutOption(it.label, dateTime = it.dateTimeIn(week), confirmMessage = it.confirmMessage)
            },
        )
    }

    fun findPendingDummiesIn(groupMatchId: Long): List<QaMember> {
        val dummyIds = qaDummies.findIds()
        val pendingDummyIds = groupMatchMemberRepository.findByRoomId(groupMatchId)
            .filter { it.isPending() && it.memberId in dummyIds }
            .map { it.memberId }
        val members = QaMembers(memberRepository.findAllById(pendingDummyIds))
        return pendingDummyIds.map(members::of)
    }

    private fun composePersonalSection(quizSets: List<QuizSet>, dummyIds: Set<Long>): QaPersonalSection {
        if (quizSets.isEmpty() || dummyIds.isEmpty()) return QaPersonalSection.EMPTY

        val titlesByQuizSetId = quizSets.associate { it.id to it.title }
        val matches = personalMatchRepository.findByQuizSetIdIn(titlesByQuizSetId.keys)
        val receivedRequests = matches
            .filter { it.isPending() && it.receiverId() in dummyIds }
            .sortedByDescending { it.id }
        val sentRequests = matches.filter { it.requesterId in dummyIds }.sortedByDescending { it.id }
        val requestableCandidates = findRequestableCandidates(titlesByQuizSetId.keys, dummyIds, matches)
        val memberIds = (receivedRequests + sentRequests).flatMap { listOf(it.memberId1, it.memberId2) } +
            requestableCandidates.flatMap { listOf(it.ownerMemberId, it.otherMemberId) }
        val rows = PersonalRows(QaMembers(memberRepository.findAllById(memberIds)), titlesByQuizSetId)

        return QaPersonalSection(
            receivedRequests = receivedRequests.map(rows::received),
            sentRequests = sentRequests.map(rows::sent),
            requestOptions = requestableCandidates.map(rows::option),
        )
    }

    /** 더미의 후보 중 실회원이고, 그 퀴즈셋에서 둘 사이에 신청이 아직 없는 쌍. */
    private fun findRequestableCandidates(
        quizSetIds: Set<Long>,
        dummyIds: Set<Long>,
        matches: List<PersonalMatch>,
    ): List<MatchCandidate> {
        val requestedPairs = matches.map { it.quizSetId to setOf(it.memberId1, it.memberId2) }.toSet()
        return matchCandidateRepository.findByQuizSetIdInAndOwnerMemberIdIn(quizSetIds, dummyIds)
            .filter { it.otherMemberId !in dummyIds }
            .filterNot { (it.quizSetId to setOf(it.ownerMemberId, it.otherMemberId)) in requestedPairs }
            .sortedWith(compareBy({ it.otherMemberId }, { it.ownerMemberId }))
    }

    /**
     * 실회원이 든 그룹을 위에, 그룹 안에서는 실회원을 위에 둔다. 화면에서 테스트 계정을 바로 찾게 하려는 것이다.
     * 성사된 그룹은 열린 방으로 바로 갈 수 있게 방 id 를 함께 준다.
     */
    private fun composeGroups(quizSets: List<QuizSet>, dummyIds: Set<Long>): List<QaGroupMatch> {
        if (quizSets.isEmpty() || dummyIds.isEmpty()) return emptyList()
        val titlesByQuizSetId = quizSets.associate { it.id to it.title }
        val groups = titlesByQuizSetId.keys.flatMap { groupMatchRepository.findByQuizSetId(it) }
        if (groups.isEmpty()) return emptyList()

        val invitationsByGroupId = groupMatchMemberRepository.findByRoomIdIn(groups.map { it.id }).groupBy { it.roomId }
        fun hasNoRealMember(group: GroupMatch) = invitationsByGroupId.getValue(group.id).all { it.memberId in dummyIds }
        val groupsWithDummy = groups
            .filter { group -> invitationsByGroupId[group.id].orEmpty().any { it.memberId in dummyIds } }
            .sortedWith(compareBy<GroupMatch> { hasNoRealMember(it) }.thenByDescending { it.id })
        val memberIds = groupsWithDummy.flatMap { invitationsByGroupId.getValue(it.id) }.map { it.memberId }
        val members = QaMembers(memberRepository.findAllById(memberIds))
        val chatRoomIdByGroupMatchId = chatRoomRepository
            .findBySourceTypeAndSourceIdIn(ChatRoomType.GROUP, groupsWithDummy.map { it.id })
            .associate { it.sourceId to it.id }

        return groupsWithDummy.map { group ->
            QaGroupMatch(
                groupMatchId = group.id,
                quizSetTitle = titlesByQuizSetId.getValue(group.quizSetId),
                acceptedCount = group.acceptedCount,
                isFormed = group.isActive,
                chatRoomId = chatRoomIdByGroupMatchId[group.id],
                members = toGroupMembers(invitationsByGroupId.getValue(group.id), members, dummyIds),
            )
        }
    }

    private fun toGroupMembers(
        invitations: List<GroupMatchMember>,
        members: QaMembers,
        dummyIds: Set<Long>,
    ): List<QaGroupMember> =
        invitations
            .sortedWith(compareBy({ it.memberId in dummyIds }, { it.memberId }))
            .map { QaGroupMember(members.of(it.memberId), isDummy = it.memberId in dummyIds, status = it.status) }

    /** 1:1 섹션의 표 한 줄씩. 세 표가 같은 이름표와 퀴즈셋 제목을 쓴다. */
    private class PersonalRows(
        private val members: QaMembers,
        private val titlesByQuizSetId: Map<Long, String>,
    ) {
        fun received(match: PersonalMatch) = QaReceivedPersonalRequest(
            matchId = match.id,
            dummy = members.of(match.receiverId()),
            requester = members.of(match.requesterId),
            quizSetTitle = titlesByQuizSetId.getValue(match.quizSetId),
            requestedAt = match.createdAt,
        )

        fun sent(match: PersonalMatch) = QaSentPersonalRequest(
            matchId = match.id,
            dummy = members.of(match.requesterId),
            receiver = members.of(match.receiverId()),
            quizSetTitle = titlesByQuizSetId.getValue(match.quizSetId),
            status = match.status,
            requestedAt = match.createdAt,
        )

        fun option(candidate: MatchCandidate) = QaPersonalRequestOption(
            dummy = members.of(candidate.ownerMemberId),
            receiver = members.of(candidate.otherMemberId),
            quizSetId = candidate.quizSetId,
            quizSetTitle = titlesByQuizSetId.getValue(candidate.quizSetId),
        )
    }
}
