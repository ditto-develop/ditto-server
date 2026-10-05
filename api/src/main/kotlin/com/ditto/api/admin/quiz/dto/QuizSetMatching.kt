package com.ditto.api.admin.quiz.dto

import com.ditto.api.match.matching.OneToOneMatchingProcessor
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import java.time.LocalDateTime
import java.util.Locale

/** 퀴즈셋에 저장된 매칭 후보와, 후보가 없는 참여자의 이유. [generatedAt]은 저장된 후보 중 가장 이른 생성 시각이다. */
class QuizSetMatching(
    val generatedAt: LocalDateTime?,
    private val byMemberId: Map<Long, ParticipantMatching>,
) {
    val isGenerated: Boolean = generatedAt != null

    fun of(memberId: Long): ParticipantMatching = byMemberId[memberId] ?: ParticipantMatching.EMPTY
}

/** [outsideRequests]는 저장된 후보와 겹치지 않는 1:1 신청·성사다. 재생성하면 후보가 바뀌어도 신청은 남는다. */
class ParticipantMatching(
    val personalCandidates: List<PersonalCandidate> = emptyList(),
    val outsideRequests: List<OutsideRequest> = emptyList(),
    val groupCandidates: List<GroupCandidate> = emptyList(),
    val miss: MatchMiss? = null,
) {
    companion object {
        val EMPTY = ParticipantMatching()

        /** 후보 상대와의 신청은 후보 줄에 붙이고, 나머지는 후보 외 신청으로 나눈다. */
        fun ofOneToOne(
            records: OneToOneRecords,
            nicknames: Map<Long, String>,
            miss: MatchMiss?,
        ): ParticipantMatching {
            val memberId = records.memberId
            val candidates = records.candidates.sortedByDescending { it.score }
            val (requestsWithCandidates, outsideRequests) = records.requests.partition { request ->
                candidates.any { it.otherMemberId == request.counterpartOf(memberId) }
            }
            return ParticipantMatching(
                personalCandidates = candidates.map { PersonalCandidate.of(it, requestsWithCandidates, nicknames) },
                outsideRequests = outsideRequests.map { OutsideRequest.of(it, memberId, nicknames) },
                miss = miss,
            )
        }
    }
}

/** 한 퀴즈셋에서 이 회원이 주인인 1:1 후보와 이 회원이 낀 1:1 신청. */
class OneToOneRecords(
    val memberId: Long,
    val candidates: List<MatchCandidate>,
    val requests: List<PersonalMatch>,
)

class PersonalCandidate(
    val otherMemberId: Long,
    val otherNickname: String?,
    val score: Double,
    val matchedQuestionCount: Int,
    val totalQuestionCount: Int,
    val requestState: PersonalRequestState?,
) {
    companion object {
        // 후보 주인이 낀 신청 중 이 후보 상대와의 신청 상태를 붙인다.
        fun of(
            candidate: MatchCandidate,
            requests: List<PersonalMatch>,
            nicknames: Map<Long, String>,
        ): PersonalCandidate {
            val request = requests.firstOrNull { it.counterpartOf(candidate.ownerMemberId) == candidate.otherMemberId }
            return PersonalCandidate(
                otherMemberId = candidate.otherMemberId,
                otherNickname = nicknames[candidate.otherMemberId],
                score = candidate.score,
                matchedQuestionCount = candidate.matchedQuestionCount,
                totalQuestionCount = candidate.totalQuestionCount,
                requestState = request?.let { PersonalRequestState.of(it, viewerId = candidate.ownerMemberId) },
            )
        }
    }
}

class OutsideRequest(
    val otherMemberId: Long,
    val otherNickname: String?,
    val requestState: PersonalRequestState,
) {
    companion object {
        fun of(request: PersonalMatch, viewerId: Long, nicknames: Map<Long, String>): OutsideRequest {
            val otherMemberId = request.counterpartOf(viewerId)
            val requestState = PersonalRequestState.of(request, viewerId)
            return OutsideRequest(otherMemberId, nicknames[otherMemberId], requestState)
        }
    }
}

/** 어드민 화면 배지. 다른 어드민 화면과 같은 뜻으로 쓴다: 성사·수락은 on, 대기는 matching, 끝난 것은 off. */
enum class BadgeTone(val cssClass: String) {
    POSITIVE("badge on"),
    PENDING("badge matching"),
    CLOSED("badge off"),
}

enum class PersonalRequestState(val label: String, val tone: BadgeTone) {
    SENT("신청함", BadgeTone.PENDING),
    RECEIVED("신청 받음", BadgeTone.PENDING),
    ACCEPTED("성사", BadgeTone.POSITIVE),
    REJECTED("거절", BadgeTone.CLOSED),
    CANCELLED("취소", BadgeTone.CLOSED),
    EXPIRED("만료", BadgeTone.CLOSED),
    ;

    companion object {
        fun of(personalMatch: PersonalMatch, viewerId: Long): PersonalRequestState = when (personalMatch.status) {
            PersonalMatchStatus.PENDING -> if (personalMatch.requesterId == viewerId) SENT else RECEIVED
            PersonalMatchStatus.ACCEPTED -> ACCEPTED
            PersonalMatchStatus.REJECTED -> REJECTED
            PersonalMatchStatus.CANCELLED -> CANCELLED
            PersonalMatchStatus.EXPIRED -> EXPIRED
        }
    }
}

class GroupCandidate(
    val groupMatchId: Long,
    val score: Double,
    val isFormed: Boolean,
    val acceptedCount: Int,
    val activationThreshold: Int,
    val myResponse: GroupResponse,
    val otherMembers: List<GroupCandidateMember>,
) {
    companion object {
        // 초대에는 보는 회원의 초대가 정확히 하나 들어 있어야 한다.
        fun of(
            groupMatch: GroupMatch,
            invitations: List<GroupMatchMember>,
            viewerId: Long,
            nicknames: Map<Long, String>,
        ): GroupCandidate {
            val (myInvitations, otherInvitations) = invitations.partition { it.memberId == viewerId }
            return GroupCandidate(
                groupMatchId = groupMatch.id,
                score = groupMatch.score,
                isFormed = groupMatch.isActive,
                acceptedCount = groupMatch.acceptedCount,
                activationThreshold = GroupMatch.ACTIVATION_THRESHOLD,
                myResponse = GroupResponse.of(myInvitations.single().status),
                otherMembers = otherInvitations.map { invitation ->
                    val response = GroupResponse.of(invitation.status)
                    GroupCandidateMember(invitation.memberId, nicknames[invitation.memberId], response)
                },
            )
        }
    }
}

class GroupCandidateMember(
    val memberId: Long,
    val nickname: String?,
    val response: GroupResponse,
)

enum class GroupResponse(val label: String, val tone: BadgeTone) {
    ACCEPTED("수락", BadgeTone.POSITIVE),
    PENDING("대기", BadgeTone.PENDING),
    DECLINED("거절", BadgeTone.CLOSED),
    ;

    companion object {
        fun of(status: InvitationStatus): GroupResponse = when (status) {
            InvitationStatus.ACCEPTED -> ACCEPTED
            InvitationStatus.PENDING -> PENDING
            InvitationStatus.DECLINED -> DECLINED
        }
    }
}

/** [bestScore]·[cutoffScore]는 상위 비율 컷에서 빠졌을 때만 채운다. */
class MatchMiss(
    val reason: MatchMissReason,
    val bestScore: Double? = null,
    val cutoffScore: Double? = null,
) {
    val scoreGap: String? = scoreGapOf(bestScore, cutoffScore)

    private fun scoreGapOf(bestScore: Double?, cutoffScore: Double?): String? {
        if (bestScore == null || cutoffScore == null) return null
        return "최고 %.1f < 컷 %.1f".format(Locale.ROOT, bestScore, cutoffScore)
    }
}

/** 이유를 얼마나 드러낼지. 예상된 결과는 흐리게, 테스터가 조치해야 하는 것은 노란 배지로 띄운다. */
enum class MissEmphasis(val cssClass: String) {
    EXPECTED("muted"),
    NORMAL(""),
    ACTION_NEEDED("badge matching"),
}

/** 후보가 없는 참여자가 매칭 단계 중 어디서 빠졌는지와, 테스터가 다음에 할 일. */
/** 다음 조치를 참여 현황 링크로 그리는 이유는 linksToParticipants 를 켠다. */
enum class MatchMissReason(
    val label: String,
    val nextAction: String?,
    val emphasis: MissEmphasis,
    val linksToParticipants: Boolean = false,
) {
    MEMBER_DELETED("삭제된 회원", null, MissEmphasis.EXPECTED),
    NOT_GENERATED("매칭 전", "매칭 화면에서 재생성하거나 배치 시각까지 기다리기", MissEmphasis.EXPECTED),
    NOT_COMPLETED("미완주", "퀴즈를 끝까지 풀기", MissEmphasis.EXPECTED),
    COMPLETED_AFTER_GENERATION(
        "매칭 이후 완주",
        "매칭 화면에서 재생성(그룹은 응답이 시작되면 재생성할 수 없음)",
        MissEmphasis.ACTION_NEEDED,
    ),
    EXCLUDED_INACTIVE("매칭 제외: 비활성 회원", "회원 상태 확인", MissEmphasis.EXPECTED),
    EXCLUDED_ALREADY_MATCHED("매칭 제외: 이미 1:1 성사", null, MissEmphasis.EXPECTED),
    EXCLUDED_OTHER("매칭 제외", null, MissEmphasis.EXPECTED),
    UNKNOWN_GENDER_OR_AGE("성별·나이 미상", "프로필에 성별·나이 입력", MissEmphasis.NORMAL),
    NO_ELIGIBLE_PAIR(
        "자격 있는 짝 없음",
        "성별 선호·나이차(${OneToOneMatchingProcessor.MAX_AGE_GAP}살 이내)·차단이 맞는 상대 추가",
        MissEmphasis.NORMAL,
    ),
    CUT_BY_TOP_RATIO("상위 비율 컷 탈락", "답을 다른 참여자와 더 맞추기", MissEmphasis.NORMAL),
    CUT_BY_HARD_LIMIT("1인 후보 수 제한에서 밀림(정상)", null, MissEmphasis.NORMAL),
    STATE_CHANGED_AFTER_GENERATION("매칭 뒤 상태 변경", "매칭 화면에서 재생성", MissEmphasis.ACTION_NEEDED),
    NOT_ASSIGNED_TO_GROUP("그룹 미배정(인원 나머지·차단, 정상)", null, MissEmphasis.NORMAL),

    /** 회원 화면은 1:1 풀 전체를 다시 계산하지 않아 풀 단계 이유를 모른다. */
    POOL_REASON_NOT_COMPUTED("후보 없음", "참여 현황에서 이유 보기", MissEmphasis.NORMAL, linksToParticipants = true),
}
