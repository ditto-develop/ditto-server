package com.ditto.api.admin.dummy.cleanup

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
        if (sanctionRemovedMembers.isEmpty()) return deleted
        val members = sanctionRemovedMembers.joinToString(", ") { "${it.label} ${it.statusText}" }
        return "$deleted 더미 신고로 걸린 제재를 지운 실회원: $members."
    }

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

/** 더미 신고로 걸린 제재가 지워진 실회원. 정리 결과에 지금 상태와 할 일을 알린다. */
class SanctionRemovedMember(
    val label: String,
    val statusText: String,
) {
    companion object {
        private const val REMAINING_SANCTION_HINT = "남은 제재가 있어 제재 관리에서 해제"

        /** 정지·차단은 걸 때 로그인이 끊겨, 풀린 뒤 앱에서 다시 로그인해야 한다. 경고만이었다면 그대로 쓴다. */
        fun of(member: Member, now: LocalDateTime, wasRestricted: Boolean) = SanctionRemovedMember(
            label = "${member.nickname}(#${member.id})",
            statusText = when {
                member.isLeft() -> "탈퇴"
                member.isPending() -> "가입 미완료"
                member.isBanned() -> "영구 차단 중($REMAINING_SANCTION_HINT)"
                member.isSuspendedAt(now) -> "이용 정지 중($REMAINING_SANCTION_HINT)"
                wasRestricted -> "정상(앱에서 다시 로그인)"
                else -> "정상"
            },
        )
    }
}
