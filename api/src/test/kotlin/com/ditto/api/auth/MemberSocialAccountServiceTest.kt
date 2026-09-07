package com.ditto.api.auth

import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorException
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import javax.sql.DataSource

class MemberSocialAccountServiceTest(
    private val memberSocialAccountService: MemberSocialAccountService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        "findOrCreateMember" - {
            "신규 사용자면 PENDING 상태의 Member와 SocialAccount를 생성한다" {
                val member = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                )

                member.id shouldNotBe 0L
                member.nickname shouldNotBe null
                member.email shouldBe "test@kakao.com"
                member.status shouldBe MemberStatus.PENDING
                memberRepository.count() shouldBe 1
                socialAccountRepository.count() shouldBe 1
            }

            "신규 사용자는 랜덤 닉네임이 부여되며 형용사+명사+숫자 형식이다" {
                val member = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-1", null, null,
                )

                member.nickname shouldNotBe null
                member.nickname.length shouldNotBe 0
            }

            "서로 다른 신규 사용자는 다른 닉네임을 가진다" {
                val member1 = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-1", null, null,
                )
                val member2 = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-2", null, null,
                )

                member1.nickname shouldNotBe member2.nickname
            }

            "신규 사용자면 카카오에서 받은 생년월일을 저장한다" {
                val birthDate = LocalDateTime.of(1995, 3, 15, 0, 0)

                val member = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", birthDate,
                )

                member.birthDate shouldBe birthDate
            }

            "신규 사용자면 카카오에서 받은 이름·전화번호·성별을 저장한다" {
                val member = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                    name = "홍길동", phoneNumber = "010-1234-5678", gender = Gender.MALE,
                )

                member.name shouldBe "홍길동"
                member.phoneNumber shouldBe "010-1234-5678"
                member.gender shouldBe Gender.MALE
            }

            "기존 사용자 재로그인 시 이름·전화번호·성별을 카카오 값으로 갱신한다" {
                val created = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                    name = "홍길동", phoneNumber = "010-1111-1111", gender = Gender.MALE,
                )

                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                    name = "김철수", phoneNumber = "010-2222-2222", gender = Gender.FEMALE,
                )

                found.id shouldBe created.id
                found.name shouldBe "김철수"
                found.phoneNumber shouldBe "010-2222-2222"
                found.gender shouldBe Gender.FEMALE
            }

            "기존 사용자 재로그인 시 이름·전화번호·성별이 null이면 기존 값을 유지한다" {
                val created = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                    name = "홍길동", phoneNumber = "010-1111-1111", gender = Gender.MALE,
                )

                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                    name = null, phoneNumber = null, gender = null,
                )

                found.id shouldBe created.id
                found.name shouldBe "홍길동"
                found.phoneNumber shouldBe "010-1111-1111"
                found.gender shouldBe Gender.MALE
            }

            "기존 사용자면 기존 Member를 반환한다" {
                val created = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                )

                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                )

                found.id shouldBe created.id
                memberRepository.count() shouldBe 1
                socialAccountRepository.count() shouldBe 1
            }

            "동의항목이 나중에 열리면 재로그인만으로 생년월일이 채워진다" {
                // 일반 앱에서는 카카오가 생년월일을 주지 않아 null로 가입된다.
                val created = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                )
                created.birthDate shouldBe null

                // 비즈 앱 전환으로 동의항목이 열린 뒤 재로그인하면 값이 들어온다.
                val birthDate = LocalDateTime.of(1995, 3, 15, 0, 0)
                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", birthDate,
                )

                found.id shouldBe created.id
                found.birthDate shouldBe birthDate
            }

            "재로그인 시 미동의 항목(null)은 기존 값을 덮지 않는다" {
                val birthDate = LocalDateTime.of(1990, 1, 2, 0, 0)
                memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", birthDate,
                    name = "김철수", phoneNumber = "010-1234-5678", gender = Gender.MALE,
                )

                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                )

                found.birthDate shouldBe birthDate
                found.name shouldBe "김철수"
                found.phoneNumber shouldBe "010-1234-5678"
                found.gender shouldBe Gender.MALE
            }

            "기존 사용자 재로그인 시 이메일이 변경되었으면 갱신한다" {
                val created = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "old@kakao.com", null,
                )

                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "new@kakao.com", null,
                )

                found.id shouldBe created.id
                found.email shouldBe "new@kakao.com"
            }

            "기존 사용자 재로그인 시 이메일이 null이면 기존 이메일을 유지한다" {
                val created = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "old@kakao.com", null,
                )

                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", null, null,
                )

                found.id shouldBe created.id
                found.email shouldBe "old@kakao.com"
            }

            "신규 사용자 이메일 없이 가입할 수 있다" {
                val member = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-456", null, null,
                )

                member.email shouldBe null
            }

            "기존 사용자 재조회 시 SocialAccount가 추가 생성되지 않는다" {
                memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                )

                memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                )

                socialAccountRepository.count() shouldBe 1
            }

            "SocialAccount는 있지만 Member가 없으면 예외가 발생한다" {
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))
                socialAccountRepository.save(
                    SocialAccount.create(
                        memberId = member.id,
                        provider = SocialProvider.KAKAO,
                        providerUserId = "kakao-123",
                    ),
                )
                memberRepository.deleteById(member.id)

                shouldThrow<ErrorException> {
                    memberSocialAccountService.findOrCreateMember(
                        SocialProvider.KAKAO, "kakao-123", "test@kakao.com", null,
                    )
                }
            }
        }
    },
)
