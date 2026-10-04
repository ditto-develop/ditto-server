package com.ditto.api.admin.quiz.dto

import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import java.time.LocalDateTime

/** 퀴즈셋에 저장된 매칭 후보와, 후보가 없는 참여자의 이유. [generatedAt]은 저장된 후보 중 가장 이른 생성 시각이다. */
class QuizSetMatching(
    val generatedAt: LocalDateTime?,
    private val byMemberId: Map<Long, ParticipantMatching>,
) {
    val isGenerated: Boolean = generatedAt != null

    fun of(memberId: Long): ParticipantMatching = byMemberId[memberId] ?: ParticipantMatching.EMPTY
}

class ParticipantMatching(
    val personalCandidates: List<PersonalCandidate> = emptyList(),
    val groupCandidate: GroupCandidate? = null,
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

enum class PersonalRequestState(val label: String) {
    SENT("신청함"),
    RECEIVED("신청 받음"),
    ACCEPTED("성사"),
    REJECTED("거절"),
    CANCELLED("취소"),
    EXPIRED("만료"),
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
    val myStatus: InvitationStatus,
    val otherMembers: List<GroupCandidateMember>,
)

class GroupCandidateMember(
    val memberId: Long,
    val nickname: String?,
    val status: InvitationStatus,
)

class MatchMiss(
    val reason: MatchMissReason,
    val detail: String? = null,
)

/** 후보가 없는 참여자가 매칭 단계 중 어디서 빠졌는지. */
enum class MatchMissReason(val label: String) {
    MEMBER_DELETED("회원 정보 없음"),
    NOT_COMPLETED("미완주"),
    COMPLETED_AFTER_GENERATION("매칭 이후 완주(재생성 필요)"),
    EXCLUDED_INACTIVE("풀 제외: 비활성"),
    EXCLUDED_ALREADY_MATCHED("풀 제외: 이미 성사"),
    UNKNOWN_GENDER_OR_AGE("성별·나이 미상"),
    NO_ELIGIBLE_PAIR("자격 있는 짝 없음(성별 선호·나이차·차단)"),
    CUT_BY_TOP_RATIO("상위 20% 컷 탈락"),
    CUT_BY_HARD_LIMIT("5명 제한·양방향 생존에서 탈락"),
    NOT_ASSIGNED_TO_GROUP("그룹 미배정(인원 나머지·차단)"),
}
