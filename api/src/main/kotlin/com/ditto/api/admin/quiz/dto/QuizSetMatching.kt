package com.ditto.api.admin.quiz.dto

import com.ditto.api.match.matching.OneToOneMatchingProcessor
import com.ditto.domain.match.entity.InvitationStatus
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
    }
}

class PersonalCandidate(
    val otherMemberId: Long,
    val otherNickname: String?,
    val score: Double,
    val matchedQuestionCount: Int,
    val totalQuestionCount: Int,
    val requestState: PersonalRequestState?,
)

class OutsideRequest(
    val otherMemberId: Long,
    val otherNickname: String?,
    val requestState: PersonalRequestState,
)

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
)

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

/** [bestScore]·[cutoffScore]는 점수 상위 비율에 못 들었을 때만 채운다. */
class MatchMiss(
    val reason: MatchMissReason,
    val bestScore: Double? = null,
    val cutoffScore: Double? = null,
) {
    val scoreGap: String? = scoreGapOf(bestScore, cutoffScore)

    private fun scoreGapOf(bestScore: Double?, cutoffScore: Double?): String? {
        if (bestScore == null || cutoffScore == null) return null
        return "최고 %.1f점, 기준 %.1f점".format(Locale.ROOT, bestScore, cutoffScore)
    }
}

/** 이유를 얼마나 드러낼지. 예상된 결과는 흐리게, 테스터가 조치해야 하는 것은 노란 배지로 띄운다. */
enum class MissEmphasis(val cssClass: String) {
    EXPECTED("muted"),
    NORMAL(""),
    ACTION_NEEDED("badge matching"),
}

/** 후보가 없는 참여자가 매칭 단계 중 어디서 빠졌는지와, 테스터가 다음에 할 일. */
enum class MatchMissReason(val label: String, val nextAction: String?, val emphasis: MissEmphasis) {
    MEMBER_DELETED("삭제된 회원", null, MissEmphasis.EXPECTED),
    NOT_GENERATED("매칭 전", "매칭 화면에서 재생성하거나 목요일 05:00 자동 매칭 기다리기", MissEmphasis.EXPECTED),
    NOT_COMPLETED("퀴즈 미완료", "퀴즈를 끝까지 풀기", MissEmphasis.EXPECTED),
    COMPLETED_AFTER_GENERATION(
        "매칭 뒤 퀴즈 완료",
        "매칭 화면에서 재생성(그룹은 응답이 시작되면 재생성할 수 없음)",
        MissEmphasis.ACTION_NEEDED,
    ),
    EXCLUDED_INACTIVE("매칭 제외: 비활성 회원", "회원 상태 확인", MissEmphasis.EXPECTED),
    EXCLUDED_ALREADY_MATCHED("매칭 제외: 이미 1:1 성사", null, MissEmphasis.EXPECTED),
    EXCLUDED_OTHER("매칭 제외", null, MissEmphasis.EXPECTED),
    UNKNOWN_GENDER_OR_AGE("성별·나이 없음", "프로필에 성별·나이 입력", MissEmphasis.NORMAL),
    NO_ELIGIBLE_PAIR(
        "조건 맞는 상대 없음",
        "성별 선호가 맞고 나이 차 ${OneToOneMatchingProcessor.MAX_AGE_GAP}살 이내이며 서로 차단하지 않은 상대 추가",
        MissEmphasis.NORMAL,
    ),
    CUT_BY_TOP_RATIO("점수 상위 비율에 못 듦", "답을 다른 참여자와 더 맞추기", MissEmphasis.NORMAL),
    CUT_BY_HARD_LIMIT("1명당 후보 수 한도에 밀림(정상)", null, MissEmphasis.NORMAL),
    STATE_CHANGED_AFTER_GENERATION("지금 다시 매칭하면 후보가 됨", "매칭 화면에서 재생성", MissEmphasis.ACTION_NEEDED),
    NOT_ASSIGNED_TO_GROUP("그룹 배정 안 됨(인원이 남거나 차단, 정상)", null, MissEmphasis.NORMAL),
}
