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
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))

                member.id shouldNotBe 0L
                member.nickname shouldBe "테스트유저"
            }

            "기본 상태는 PENDING이다" {
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))

                member.status shouldBe MemberStatus.PENDING
                member.isPending() shouldBe true
            }
        }

        "Member 이메일" - {
            "hasEmailChanged() - 이메일이 다르면 true를 반환한다" {
                val member = Member(nickname = "테스트유저", email = "old@kakao.com")

                member.hasEmailChanged("new@kakao.com") shouldBe true
            }

            "hasEmailChanged() - 이메일이 같으면 false를 반환한다" {
                val member = Member(nickname = "테스트유저", email = "same@kakao.com")

                member.hasEmailChanged("same@kakao.com") shouldBe false
            }

            "hasEmailChanged() - 새 이메일이 null이면 false를 반환한다" {
                val member = Member(nickname = "테스트유저", email = "old@kakao.com")

                member.hasEmailChanged(null) shouldBe false
            }

            "updateEmail() - 이메일을 변경한다" {
                val member = Member(nickname = "테스트유저", email = "old@kakao.com")

                member.updateEmail("new@kakao.com")

                member.email shouldBe "new@kakao.com"
            }

            "updateEmail() - null이면 이메일을 변경하지 않는다" {
                val member = Member(nickname = "테스트유저", email = "old@kakao.com")

                member.updateEmail(null)

                member.email shouldBe "old@kakao.com"
            }

            "이메일 없이 Member를 생성할 수 있다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))

                member.email shouldBe null
            }
        }

        "Member 상태 변경" - {
            "activate() 호출 시 ACTIVE 상태로 변경된다" {
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))

                member.activate()
                memberRepository.save(member)

                val found = memberRepository.findById(member.id).get()
                found.status shouldBe MemberStatus.ACTIVE
                found.isPending() shouldBe false
            }
        }
    },
)
