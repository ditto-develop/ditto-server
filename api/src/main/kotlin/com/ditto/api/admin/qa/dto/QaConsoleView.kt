package com.ditto.api.admin.qa.dto

import com.ditto.common.exception.ErrorCode
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.entity.PersonalMatchStatus
import java.time.LocalDate
import java.time.LocalDateTime

class QaConsoleView(
    val now: LocalDateTime,
    val isTimeOverridden: Boolean,
    val weekStartedOn: LocalDate,
    val dummyCount: Int,
    val personal: QaPersonalSection,
    val group: QaGroupSection,
    val timeline: QaTimeline,
) {
    /** 1:1 신청과 그룹 초대에 나온 실회원. 아직 방이 없는 테스트 계정도 여기서 보인다. */
    val realMembersInRequestsAndGroups: List<QaMember> =
        (
            personal.receivedRequests.map { it.requester } +
                personal.sentRequests.map { it.receiver } +
                personal.requestOptions.map { it.receiver } +
                group.groups.flatMap { group -> group.members.filterNot { it.isDummy }.map { it.member } }
            ).distinctBy { it.id }
}

/** restriction 은 앱을 쓸 수 없는 이유(정지·영구 차단·탈퇴). 콘솔도 그 회원으로는 움직이지 못한다. */
class QaMember(
    val id: Long,
    val nickname: String,
    val restriction: String? = null,
) {
    /** 결과 메시지용. 화면의 닉네임과 로그의 id 를 함께 대조할 수 있게 한다. */
    val label: String = "$nickname(#$id)"

    val isRestricted: Boolean = restriction != null

    /** 배지를 달 수 없는 select 보기용. */
    val nicknameWithRestriction: String = if (restriction == null) nickname else "$nickname ($restriction)"

    val labelWithRestriction: String = if (restriction == null) label else "$label ($restriction)"
}

class QaPersonalSection(
    val receivedRequests: List<QaReceivedPersonalRequest>,
    val sentRequests: List<QaSentPersonalRequest>,
    val requestOptions: List<QaPersonalRequestOption>,
) {
    companion object {
        val EMPTY = QaPersonalSection(emptyList(), sentRequests = emptyList(), requestOptions = emptyList())
    }
}

class QaReceivedPersonalRequest(
    val matchId: Long,
    val dummy: QaMember,
    val requester: QaMember,
    val quizSetTitle: String,
    val requestedAt: LocalDateTime,
)

/** 더미가 보낸 신청. 상대(테스터)가 수락·거절했는지 앱 밖에서 확인하는 용도라 상태와 관계없이 모두 보여준다. */
class QaSentPersonalRequest(
    val matchId: Long,
    val dummy: QaMember,
    val receiver: QaMember,
    val quizSetTitle: String,
    val status: PersonalMatchStatus,
    val requestedAt: LocalDateTime,
)

/** 더미가 신청을 보낼 수 있는 실회원. 더미의 이번 주 후보 중 아직 신청이 오가지 않은 사람이다. */
class QaPersonalRequestOption(
    val dummy: QaMember,
    val receiver: QaMember,
    val quizSetId: Long,
    val quizSetTitle: String,
)

class QaGroupSection(
    val groups: List<QaGroupMatch>,
    val responseDeadline: LocalDateTime,
    val isResponseClosed: Boolean,
) {
    val closedErrorCode: String = ErrorCode.NOT_MATCHING_PERIOD.code

    /** 마감 안내는 아직 응답할 초대가 남았을 때만 의미가 있다. 금요일 이후 채팅 QA 중에는 정상 상태다. */
    val hasPendingInvitation: Boolean =
        groups.any { group -> group.members.any { it.isPending } }
}

/** 더미가 한 명 이상 들어 있는 이번 주 후보 그룹. */
class QaGroupMatch(
    val groupMatchId: Long,
    val quizSetTitle: String,
    val acceptedCount: Int,
    val isFormed: Boolean,
    val chatRoomId: Long?,
    val members: List<QaGroupMember>,
) {
    val requiredCount: Int = GroupMatch.ACTIVATION_THRESHOLD

    val hasPendingDummy: Boolean = members.any { it.isDummy && it.isPending }
}

class QaGroupMember(
    val member: QaMember,
    val isDummy: Boolean,
    val status: InvitationStatus,
) {
    val isPending: Boolean = status == InvitationStatus.PENDING
}
