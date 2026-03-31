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

            "기본 상태는 PENDING이다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))

                member.status shouldBe MemberStatus.PENDING
                member.isPending() shouldBe true
            }
        }

        "Member 상태 변경" - {
            "activate() 호출 시 ACTIVE 상태로 변경된다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))

                member.activate()
                memberRepository.save(member)

                val found = memberRepository.findById(member.id).get()
                found.status shouldBe MemberStatus.ACTIVE
                found.isPending() shouldBe false
            }
        }
    },
)
