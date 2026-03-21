package com.ditto.domain.member

import com.ditto.domain.withId

object MemberFixture {

    fun create(
        nickname: String = "테스트유저",
        id: Long = 0L,
    ): Member = Member(
        nickname = nickname,
        id = id,
    ).withId(id)
}
