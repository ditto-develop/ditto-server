package com.ditto.domain.match.repository

import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

/** `findPairMemberIdsById` — 1:1 수락 전에 잠글 회원을 고르는 조회. */
class PersonalMatchPairMemberIdsQueryTest(
    private val personalMatchRepository: PersonalMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "정규화된 두 회원 ID를 작은 쪽부터 돌려준다" {
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 20L, receiverId = 10L, quizSetId = 1L),
        )

        personalMatchRepository.findPairMemberIdsById(match.id) shouldBe (10L to 20L)
    }

    "없는 매칭이면 null 이다" {
        personalMatchRepository.findPairMemberIdsById(9999L) shouldBe null
    }
})
