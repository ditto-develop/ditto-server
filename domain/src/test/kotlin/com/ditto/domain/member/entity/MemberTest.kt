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

        "Member 소셜 개인정보 갱신" - {
            "updateOAuthInfo() - 이름·전화번호·성별을 갱신한다" {
                val member = Member(nickname = "테스트유저")

                member.updateOAuthInfo(name = "홍길동", phoneNumber = "010-1234-5678", gender = Gender.FEMALE)

                member.name shouldBe "홍길동"
                member.phoneNumber shouldBe "010-1234-5678"
                member.gender shouldBe Gender.FEMALE
            }

            "updateOAuthInfo() - null 값인 필드는 기존 값을 유지한다" {
                val member = Member(
                    nickname = "테스트유저",
                    name = "기존이름",
                    phoneNumber = "010-1111-1111",
                    gender = Gender.MALE,
                )

                member.updateOAuthInfo(name = null, phoneNumber = null, gender = null)

                member.name shouldBe "기존이름"
                member.phoneNumber shouldBe "010-1111-1111"
                member.gender shouldBe Gender.MALE
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

        "Member 관심사/사는곳/직업" - {
            "관심사 여러 개를 저장하고 다시 조회할 수 있다" {
                val member = memberRepository.save(
                    Member(
                        nickname = "테스트유저",
                        interests = setOf(Interest.TRAVEL, Interest.MUSIC, Interest.GAMING),
                    ),
                )

                val found = memberRepository.findById(member.id).get()
                found.interests shouldBe setOf(Interest.TRAVEL, Interest.MUSIC, Interest.GAMING)
            }

            "관심사를 지정하지 않으면 빈 집합이다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))

                val found = memberRepository.findById(member.id).get()
                found.interests shouldBe emptySet()
            }

            "사는곳과 직업을 저장하고 다시 조회할 수 있다" {
                val member = memberRepository.save(
                    Member(
                        nickname = "테스트유저",
                        location = Location.SEOUL,
                        job = Job.IT_TECH,
                    ),
                )

                val found = memberRepository.findById(member.id).get()
                found.location shouldBe Location.SEOUL
                found.job shouldBe Job.IT_TECH
            }

            "사는곳과 직업은 지정하지 않으면 null이다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))

                val found = memberRepository.findById(member.id).get()
                found.location shouldBe null
                found.job shouldBe null
            }
        }

        "Member 회원가입" - {
            "register() 호출 시 ACTIVE 상태로 변경되고 필드가 채워진다" {
                val member = memberRepository.save(Member(nickname = "임시닉네임"))

                member.register(
                    name = "김철수",
                    nickname = "철수123",
                    phoneNumber = "010-1234-5678",
                    gender = Gender.MALE,
                    age = 25,
                    birthDate = null,
                    email = "test@example.com",
                    interests = setOf(Interest.TRAVEL, Interest.MUSIC),
                    location = Location.SEOUL,
                    job = Job.IT_TECH,
                    caricature = "m1",
                )
                memberRepository.save(member)

                val found = memberRepository.findById(member.id).get()
                found.status shouldBe MemberStatus.ACTIVE
                found.name shouldBe "김철수"
                found.nickname shouldBe "철수123"
                found.phoneNumber shouldBe "010-1234-5678"
                found.gender shouldBe Gender.MALE
                found.age shouldBe 25
                found.email shouldBe "test@example.com"
                found.interests shouldBe setOf(Interest.TRAVEL, Interest.MUSIC)
                found.location shouldBe Location.SEOUL
                found.job shouldBe Job.IT_TECH
                found.caricature shouldBe "m1"
                found.joinedAt shouldNotBe null
            }

            "register() - null 값인 필드는 기존 값을 유지한다" {
                val member = Member(nickname = "임시닉네임", email = "original@kakao.com")

                member.register(
                    name = null,
                    nickname = null,
                    phoneNumber = null,
                    gender = null,
                    age = null,
                    birthDate = null,
                    email = null,
                    interests = setOf(Interest.READING),
                    location = Location.BUSAN,
                    job = Job.STUDENT,
                    caricature = "f2",
                )

                member.nickname shouldBe "임시닉네임"
                member.email shouldBe "original@kakao.com"
                member.interests shouldBe setOf(Interest.READING)
                member.location shouldBe Location.BUSAN
                member.job shouldBe Job.STUDENT
                member.status shouldBe MemberStatus.ACTIVE
                member.joinedAt shouldNotBe null
            }
        }

    },
)
