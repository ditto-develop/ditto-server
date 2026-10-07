package com.ditto.api.admin.dummy.cleanup

import com.ditto.api.admin.qa.AppUnavailability
import com.ditto.domain.member.entity.Member
import java.time.LocalDateTime

class DummyCleanupSummary(
    val dummyCount: Int,
    val roomCount: Int,
    val matchCount: Int,
    val reportCount: Int,
    val sanctionCount: Int,
    val notificationCount: Int,
    val sanctionRemovedMembers: List<SanctionRemovedMember>,
) {
    fun toDisplayText(): String =
        "더미 ${dummyCount}명, 채팅방 ${roomCount}개, 매칭 ${matchCount}건, " +
            "신고 ${reportCount}건, 제재 ${sanctionCount}건, 알림 ${notificationCount}개"

    fun toResultMessage(): String {
        val deleted = "${toDisplayText()}를 삭제했습니다."
        val settledMembers = sanctionRemovedMembers.filterNot { it.needsFollowUp }
        if (settledMembers.isEmpty()) return deleted
        return "$deleted 더미 신고로 걸린 제재를 지운 실회원: ${describe(settledMembers)}."
    }

    /** 제재가 남아 할 일이 있는 회원은 성공 알림에 묻히지 않게 따로 경고한다. */
    fun toFollowUpWarning(): String? {
        val followUpMembers = sanctionRemovedMembers.filter { it.needsFollowUp }
        if (followUpMembers.isEmpty()) return null
        val pages = followUpMembers.joinToString(", ") { "/admin/members/${it.memberId}/sanctions" }
        return "더미 신고 제재를 지웠지만 제재가 남은 실회원: ${describe(followUpMembers)}. 제재 관리($pages)에서 확인하세요."
    }

    private fun describe(members: List<SanctionRemovedMember>): String =
        members.joinToString(", ") { "${it.label} ${it.stateAndNextStep}" }

    companion object {
        val NONE = DummyCleanupSummary(
            dummyCount = 0,
            roomCount = 0,
            matchCount = 0,
            reportCount = 0,
            sanctionCount = 0,
            notificationCount = 0,
            sanctionRemovedMembers = emptyList(),
        )
    }
}

/** 정리로 이 회원에게서 빠진 것과 남은 것. */
class SanctionRemovalFacts(
    val liftedSuspensionOrBan: Boolean,
    val hasRemainingWarning: Boolean,
)

/** 더미 신고로 걸린 제재가 지워진 실회원. 정리 결과에 지금 상태와 할 일을 알린다. */
class SanctionRemovedMember(
    val memberId: Long,
    val label: String,
    val stateAndNextStep: String,
    val needsFollowUp: Boolean,
) {
    companion object {
        fun of(member: Member, now: LocalDateTime, facts: SanctionRemovalFacts): SanctionRemovedMember {
            val unavailability = AppUnavailability.of(member, now)
            val stateAndNextStep = when {
                unavailability?.isSanction == true -> "${unavailability.text} 상태(남은 제재가 있어 제재 관리에서 해제)"
                unavailability != null -> unavailability.text
                member.isPending() -> "가입 미완료"
                else -> normalStateOf(facts)
            }
            return SanctionRemovedMember(
                memberId = member.id,
                label = "${member.nickname}(#${member.id})",
                stateAndNextStep = stateAndNextStep,
                needsFollowUp = unavailability?.isSanction == true || facts.hasRemainingWarning,
            )
        }

        /** 정지·차단은 걸 때 로그인이 끊겨, 이번에 풀렸다면 앱에서 다시 로그인해야 한다. */
        private fun normalStateOf(facts: SanctionRemovalFacts): String {
            val notes = listOfNotNull(
                "앱에서 다시 로그인".takeIf { facts.liftedSuspensionOrBan },
                "남은 경고로 다음 주 퀴즈 차단".takeIf { facts.hasRemainingWarning },
            )
            return if (notes.isEmpty()) "정상" else "정상(${notes.joinToString(", ")})"
        }
    }
}
