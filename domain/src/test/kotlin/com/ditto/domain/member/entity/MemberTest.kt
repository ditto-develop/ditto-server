package com.ditto.domain.member.entity

import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class MemberTest(
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        "Member 생성" - {
            "Member를 생성하고 저장할 수 있다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))

                member.id shouldNotBe 0L
                member.nickname shouldBe "테스트유저"
            }
        }
    },
)
