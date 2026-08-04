package com.ditto.domain.match.repository

import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

/**
 * `existsByMemberIdAndStatusIn` — 탈퇴 가드가 쓰는 판정.
 * 페어가 (memberId1, memberId2)로 정규화돼 있어 양쪽 컬럼을 모두 보는지 확인한다.
 */
class PersonalMatchOngoingQueryTest(
    private val personalMatchRepository: PersonalMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val ongoing = setOf(PersonalMatchStatus.PENDING, PersonalMatchStatus.ACCEPTED)

    "요청자로 낀 진행 중 매칭을 찾는다" {
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 10L, receiverId = 20L, status = PersonalMatchStatus.ACCEPTED),
        )

        personalMatchRepository.existsByMemberIdAndStatusIn(10L, ongoing) shouldBe true
    }

    "수신자로 낀 진행 중 매칭도 찾는다 — 방향 무관" {
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 10L, receiverId = 20L, status = PersonalMatchStatus.ACCEPTED),
        )

        personalMatchRepository.existsByMemberIdAndStatusIn(20L, ongoing) shouldBe true
    }

    "거절된 매칭만 있으면 거짓이다" {
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 10L, receiverId = 20L, status = PersonalMatchStatus.REJECTED),
        )

        personalMatchRepository.existsByMemberIdAndStatusIn(10L, ongoing) shouldBe false
    }

    "매칭에 끼지 않은 회원은 거짓이다" {
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 10L, receiverId = 20L, status = PersonalMatchStatus.ACCEPTED),
        )

        personalMatchRepository.existsByMemberIdAndStatusIn(99L, ongoing) shouldBe false
    }

    "상태 목록이 비어 있으면 쿼리 없이 거짓이다" {
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 10L, receiverId = 20L, status = PersonalMatchStatus.ACCEPTED),
        )

        personalMatchRepository.existsByMemberIdAndStatusIn(10L, emptySet()) shouldBe false
    }
})
