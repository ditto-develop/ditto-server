package com.ditto.domain.member

import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.withId

object MemberFixture {

    fun create(
        nickname: String = "테스트유저",
        email: String = "test@example.com",
        status: MemberStatus = MemberStatus.PENDING,
        id: Long = 0L,
    ): Member = Member(
        nickname = nickname,
        email = email,
        status = status,
        id = id,
    ).withId(id)
}
