package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.domain.member.entity.Member
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 화면에 띄울 회원 이름표. 지워진 회원이 남긴 행도 화면이 깨지지 않게 id 로 대신 보여준다. */
class QaMembers(members: List<Member>, now: LocalDateTime) {
    private val membersById = members.associate { it.id to QaMember(it.id, it.nickname, restrictionOf(it, now)) }

    fun of(memberId: Long): QaMember = membersById[memberId] ?: QaMember(memberId, "없는 회원 #$memberId")

    private companion object {
        val SUSPENDED_UNTIL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")

        /** 앱 인증 필터가 막는 상태와 같다. */
        fun restrictionOf(member: Member, now: LocalDateTime): String? = when {
            member.isLeft() -> "탈퇴"
            member.isBanned() -> "영구 차단"
            member.isSuspendedAt(now) ->
                member.suspendedUntil?.let { "이용 정지 ~${SUSPENDED_UNTIL_FORMATTER.format(it)}" } ?: "이용 정지"
            else -> null
        }
    }
}
