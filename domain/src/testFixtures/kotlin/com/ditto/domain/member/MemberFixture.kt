package com.ditto.domain.member

import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.withId

object MemberFixture {

    fun create(
        nickname: String = "테스트유저",
        status: MemberStatus = MemberStatus.PENDING,
        id: Long = 0L,
    ): Member = Member(
        nickname = nickname,
        status = status,
        id = id,
    ).withId(id)
}
