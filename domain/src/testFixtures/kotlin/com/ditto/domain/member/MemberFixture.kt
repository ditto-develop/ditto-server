package com.ditto.domain.member

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.withId

object MemberFixture {

    fun create(
        nickname: String = "테스트유저",
        email: String = "test@example.com",
        status: MemberStatus = MemberStatus.PENDING,
        role: MemberRole = MemberRole.USER,
        gender: Gender? = null,
        age: Int? = null,
        interests: Set<Interest> = emptySet(),
        location: Location? = null,
        job: Job? = null,
        caricature: String? = null,
        id: Long = 0L,
    ): Member = Member(
        nickname = nickname,
        email = email,
        status = status,
        role = role,
        gender = gender,
        age = age,
        interests = interests,
        location = location,
        job = job,
        caricature = caricature,
        id = id,
    ).withId(id)
}
