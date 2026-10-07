package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import java.time.LocalDateTime

/** 화면에 띄울 회원 이름표. 지워진 회원이 남긴 행도 화면이 깨지지 않게 id 로 대신 보여준다. */
class QaMembers(members: List<Member>, now: LocalDateTime) {
    private val membersById = members.associate { it.id to it.toQaMember(now) }

    fun of(memberId: Long): QaMember = membersById[memberId] ?: QaMember(memberId, "없는 회원 #$memberId")

    private fun Member.toQaMember(now: LocalDateTime) = QaMember(
        id = id,
        nickname = nickname,
        unavailability = AppUnavailability.of(this, now),
        isSuspensionOverdue = status == MemberStatus.SUSPENDED && !isSuspendedAt(now),
    )
}
