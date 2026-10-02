package com.ditto.domain.match.repository

import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import javax.sql.DataSource

/** `findAllByQuizSetIdAndStatusAndMemberIdIn` — 1:1 성사 시 남은 신청을 고르는 조회. */
class PersonalMatchByMembersQueryTest(
    private val personalMatchRepository: PersonalMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "주어진 회원이 요청자든 수신자든 낀 매칭을 모두 찾는다" {
        val sent = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 10L, receiverId = 30L, quizSetId = 1L),
        )
        val received = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 40L, receiverId = 20L, quizSetId = 1L),
        )
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 50L, receiverId = 60L, quizSetId = 1L),
        )

        personalMatchRepository.findAllByQuizSetIdAndStatusAndMemberIdIn(
            quizSetId = 1L,
            status = PersonalMatchStatus.PENDING,
            memberIds = listOf(10L, 20L),
        ).map { it.id } shouldContainExactlyInAnyOrder listOf(sent.id, received.id)
    }

    "다른 상태나 다른 퀴즈셋의 매칭은 찾지 않는다" {
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = 10L, receiverId = 30L, quizSetId = 1L,
                status = PersonalMatchStatus.REJECTED,
            ),
        )
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 10L, receiverId = 40L, quizSetId = 2L),
        )

        personalMatchRepository.findAllByQuizSetIdAndStatusAndMemberIdIn(
            quizSetId = 1L,
            status = PersonalMatchStatus.PENDING,
            memberIds = listOf(10L),
        ).shouldBeEmpty()
    }
})
