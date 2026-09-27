package com.ditto.domain.member.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.FreeSpec

class MemberProfileUpdateTest : FreeSpec(
    {
        "updateProfile" - {
            "캐리커쳐와 관심사를 함께 바꾼다" {
                val member = MemberFixture.create(
                    status = MemberStatus.ACTIVE,
                    interests = setOf(Interest.WORKOUT),
                    caricature = "/assets/avatar/m1.png",
                )

                member.updateProfile(
                    ProfileChanges(
                        caricature = "/assets/avatar/m3.png",
                        interests = setOf(Interest.MOVIE_DRAMA, Interest.EXHIBITION),
                    ),
                )

                member.caricature shouldBe "/assets/avatar/m3.png"
                member.interests shouldBe setOf(Interest.MOVIE_DRAMA, Interest.EXHIBITION)
            }

            "null인 항목은 기존 값을 유지한다" {
                val member = MemberFixture.create(
                    status = MemberStatus.ACTIVE,
                    interests = setOf(Interest.WORKOUT),
                    caricature = "/assets/avatar/m1.png",
                )

                member.updateProfile(ProfileChanges())

                member.caricature shouldBe "/assets/avatar/m1.png"
                member.interests shouldBe setOf(Interest.WORKOUT)
            }

            "관심사만 바꿀 수 있다" {
                val member = MemberFixture.create(
                    status = MemberStatus.ACTIVE,
                    interests = setOf(Interest.WORKOUT),
                    caricature = "/assets/avatar/m1.png",
                )

                member.updateProfile(ProfileChanges(interests = setOf(Interest.MUSIC)))

                member.interests shouldBe setOf(Interest.MUSIC)
                member.caricature shouldBe "/assets/avatar/m1.png"
            }

            "성별·사는곳·직업을 바꾼다" {
                val member = MemberFixture.create(
                    status = MemberStatus.ACTIVE,
                    gender = Gender.MALE,
                    location = Location.SEOUL,
                    job = Job.IT_TECH,
                )

                member.updateProfile(
                    ProfileChanges(
                        gender = Gender.FEMALE,
                        location = Location.BUSAN,
                        job = Job.DESIGN,
                    ),
                )

                member.gender shouldBe Gender.FEMALE
                member.location shouldBe Location.BUSAN
                member.job shouldBe Job.DESIGN
            }

            "관심사를 빈 집합으로 지울 수 없다 — 온보딩 필수 정보다" {
                val member = MemberFixture.create(
                    status = MemberStatus.ACTIVE,
                    interests = setOf(Interest.WORKOUT),
                )

                val exception = shouldThrow<WarnException> {
                    member.updateProfile(ProfileChanges(interests = emptySet()))
                }

                exception.errorCode shouldBe ErrorCode.BAD_REQUEST
                // 실패해도 기존 값은 그대로다.
                member.interests shouldBe setOf(Interest.WORKOUT)
            }
        }
    },
)
