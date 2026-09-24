package com.ditto.domain.member.repository

import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class MemberRepositoryTest(
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun save(nickname: String, status: MemberStatus) =
        memberRepository.save(MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = status))

    "findAllIdsByStatus" - {
        "해당 상태인 회원의 ID 만 돌려준다" {
            val active = listOf(save("a", MemberStatus.ACTIVE), save("b", MemberStatus.ACTIVE))
            save("left", MemberStatus.LEFT)
            save("pending", MemberStatus.PENDING)

            memberRepository.findAllIdsByStatus(MemberStatus.ACTIVE).toSet() shouldBe active.map { it.id }.toSet()
        }

        "해당 상태인 회원이 없으면 빈 목록이다" {
            save("left", MemberStatus.LEFT)

            memberRepository.findAllIdsByStatus(MemberStatus.ACTIVE).shouldBeEmpty()
        }
    }
})
