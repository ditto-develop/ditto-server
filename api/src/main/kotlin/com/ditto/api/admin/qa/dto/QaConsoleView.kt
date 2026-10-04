package com.ditto.api.admin.qa.dto

import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.InvitationStatus
import java.time.LocalDate
import java.time.LocalDateTime

class QaConsoleView(
    val now: LocalDateTime,
    val weekStartedOn: LocalDate,
    val dummyCount: Int,
    val personal: QaPersonalSection,
    val group: QaGroupSection,
    val timeShortcuts: List<QaTimeShortcutOption>,
)

class QaTimeShortcutOption(
    val label: String,
    val dateTime: LocalDateTime,
    val confirmMessage: String?,
)

class QaMember(
    val id: Long,
    val nickname: String,
)

class QaPersonalSection(
    val receivedRequests: List<DummyReceivedPersonalRequest>,
    val requestOptions: List<DummyPersonalRequestOption>,
) {
    companion object {
        val EMPTY = QaPersonalSection(receivedRequests = emptyList(), requestOptions = emptyList())
    }
}

class DummyReceivedPersonalRequest(
    val matchId: Long,
    val dummy: QaMember,
    val requester: QaMember,
    val quizSetTitle: String,
    val requestedAt: LocalDateTime,
)

/** 더미가 신청을 보낼 수 있는 실회원. 더미의 이번 주 후보 중 아직 신청이 오가지 않은 사람이다. */
class DummyPersonalRequestOption(
    val dummy: QaMember,
    val receiver: QaMember,
    val quizSetId: Long,
    val quizSetTitle: String,
)

class QaGroupSection(
    val groups: List<QaGroupMatch>,
    val responseClosed: Boolean,
) {
    /** 마감 안내는 아직 응답할 초대가 남았을 때만 의미가 있다. 금요일 이후 채팅 QA 중에는 정상 상태다. */
    val hasPendingInvitation: Boolean = groups.any { group -> group.members.any { it.status == InvitationStatus.PENDING } }
}

/** 더미가 한 명 이상 들어 있는 이번 주 후보 그룹. */
class QaGroupMatch(
    val groupMatchId: Long,
    val quizSetTitle: String,
    val acceptedCount: Int,
    val formed: Boolean,
    val chatRoomId: Long?,
    val members: List<QaGroupMember>,
) {
    val requiredCount: Int = GroupMatch.ACTIVATION_THRESHOLD

    val hasPendingDummy: Boolean = members.any { it.dummy && it.status == InvitationStatus.PENDING }
}

class QaGroupMember(
    val member: QaMember,
    val dummy: Boolean,
    val status: InvitationStatus,
)
