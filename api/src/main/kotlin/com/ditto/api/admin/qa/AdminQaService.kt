package com.ditto.api.admin.qa

import com.ditto.api.admin.dummy.AdminDummyService
import com.ditto.api.admin.qa.dto.DummyPersonalRequestOption
import com.ditto.api.admin.qa.dto.DummyReceivedPersonalRequest
import com.ditto.api.admin.qa.dto.QaConsoleView
import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.api.admin.qa.dto.QaPersonalSection
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.match.MatchWeekPolicy
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** QA 콘솔 화면 조회와 더미 확인. 더미가 실제로 움직이는 쓰기는 [AdminQaController]가 맡는다. */
@Service
@Transactional(readOnly = true)
class AdminQaService(
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val matchWeekPolicy: MatchWeekPolicy,
) {
    fun getConsole(): QaConsoleView {
        val weekStartedOn = matchWeekPolicy.currentWeek().startedOn
        val dummyIds = memberRepository.findByNicknameStartingWith(AdminDummyService.NICKNAME_PREFIX)
            .map { it.id }
            .toSet()
        val quizSets = quizSetRepository.findByWeekStartedOn(weekStartedOn)
        return QaConsoleView(
            weekStartedOn = weekStartedOn,
            dummyCount = dummyIds.size,
            personal = composePersonalSection(quizSets.filter { it.matchingType == MatchingType.ONE_TO_ONE }, dummyIds),
        )
    }

    /** 실회원으로는 움직이지 않는다. 닉네임 규칙이 '-'를 막아 실회원은 더미 접두어를 가질 수 없다. */
    fun dummyPrincipalOf(memberId: Long): MemberPrincipal {
        val member = memberRepository.findByIdOrNull(memberId) ?: throw WarnException(ErrorCode.NOT_FOUND)
        if (!member.nickname.startsWith(AdminDummyService.NICKNAME_PREFIX)) {
            throw WarnException(ErrorCode.FORBIDDEN, "더미 회원만 대신 움직일 수 있습니다.")
        }
        return MemberPrincipal(member.id)
    }

    private fun composePersonalSection(quizSets: List<QuizSet>, dummyIds: Set<Long>): QaPersonalSection {
        if (quizSets.isEmpty() || dummyIds.isEmpty()) return QaPersonalSection.EMPTY

        val titlesByQuizSetId = quizSets.associate { it.id to it.title }
        val matches = personalMatchRepository.findByQuizSetIdIn(titlesByQuizSetId.keys)
        val receivedRequests = matches
            .filter { it.isPending() && it.receiverId() in dummyIds }
            .sortedByDescending { it.id }
        val requestableCandidates = findRequestableCandidates(titlesByQuizSetId.keys, dummyIds, matches)
        val members = QaMembers(
            memberRepository.findAllById(
                receivedRequests.flatMap { listOf(it.memberId1, it.memberId2) } +
                    requestableCandidates.flatMap { listOf(it.ownerMemberId, it.otherMemberId) },
            ),
        )

        return QaPersonalSection(
            receivedRequests = receivedRequests.map { match ->
                DummyReceivedPersonalRequest(
                    matchId = match.id,
                    dummy = members.of(match.receiverId()),
                    requester = members.of(match.requesterId),
                    quizSetTitle = titlesByQuizSetId.getValue(match.quizSetId),
                    requestedAt = match.createdAt,
                )
            },
            requestOptions = requestableCandidates.map { candidate ->
                DummyPersonalRequestOption(
                    dummy = members.of(candidate.ownerMemberId),
                    receiver = members.of(candidate.otherMemberId),
                    quizSetId = candidate.quizSetId,
                    quizSetTitle = titlesByQuizSetId.getValue(candidate.quizSetId),
                )
            },
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

    private class QaMembers(members: List<Member>) {
        private val membersById = members.associate { it.id to QaMember(it.id, it.nickname) }

        fun of(memberId: Long): QaMember = membersById[memberId] ?: QaMember(memberId, "없는 회원 #$memberId")
    }
}
