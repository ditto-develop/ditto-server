package com.ditto.domain.member.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.sanction.SanctionFixture
import com.ditto.domain.sanction.entity.SanctionLevel
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class MemberSanctionTest : FreeSpec(
    {
        val until = LocalDateTime.of(2026, 7, 26, 23, 59, 59)

        "suspendUntil" - {
            "활성 회원을 정지하면 SUSPENDED가 되고 해제 예정 일시가 저장된다" {
                val member = MemberFixture.create(status = MemberStatus.ACTIVE)

                member.suspendUntil(until)

                member.status shouldBe MemberStatus.SUSPENDED
                member.suspendedUntil shouldBe until
            }

            "영구 차단 회원은 정지로 낮출 수 없고, 차단을 먼저 풀라고 알린다" {
                val member = MemberFixture.create(status = MemberStatus.BANNED)

                val exception = shouldThrow<WarnException> {
                    member.suspendUntil(until)
                }

                exception.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                exception.message shouldBe "영구 차단 중인 회원은 정지할 수 없습니다. 차단을 먼저 해제하세요."
            }
        }

        "suspendUntil 은 탈퇴·가입 미완료 회원을 거부한다" - {
            "탈퇴한 회원은 정지하지 않고 다른 처리를 안내한다" {
                val member = MemberFixture.create(status = MemberStatus.LEFT)

                val exception = shouldThrow<WarnException> { member.suspendUntil(until) }

                exception.message shouldBe "탈퇴한 회원은 정지할 수 없습니다. 경고나 기각으로 처리하세요."
                member.status shouldBe MemberStatus.LEFT
            }

            "가입을 마치지 않은 회원은 정지할 수 없다" {
                val member = MemberFixture.create(status = MemberStatus.PENDING)

                val exception = shouldThrow<WarnException> { member.suspendUntil(until) }

                exception.message shouldBe "가입을 마치지 않은 회원은 정지할 수 없습니다."
                member.status shouldBe MemberStatus.PENDING
            }
        }

        "ban" - {
            "영구 차단하면 BANNED가 되고 해제 예정 일시는 비워진다" {
                val member = MemberFixture.create(status = MemberStatus.SUSPENDED, suspendedUntil = until)

                member.ban()

                member.status shouldBe MemberStatus.BANNED
                member.suspendedUntil.shouldBeNull()
            }

            "이미 영구 차단된 회원은 다시 차단할 수 없다" {
                val member = MemberFixture.create(status = MemberStatus.BANNED)

                val exception = shouldThrow<WarnException> {
                    member.ban()
                }

                exception.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                exception.message shouldBe "이미 영구 차단된 회원입니다."
            }

            "가입 미완료(PENDING) 회원은 차단할 수 없다" {
                val member = MemberFixture.create(status = MemberStatus.PENDING)

                val exception = shouldThrow<WarnException> {
                    member.ban()
                }

                exception.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
            }
        }

        "reinstate" - {
            "정지 회원을 해제하면 ACTIVE로 원복되고 해제 예정 일시가 비워진다" {
                val member = MemberFixture.create(status = MemberStatus.SUSPENDED, suspendedUntil = until)

                member.reinstate()

                member.status shouldBe MemberStatus.ACTIVE
                member.suspendedUntil.shouldBeNull()
            }

            "영구 차단 회원도 어드민 직권으로 해제할 수 있다" {
                val member = MemberFixture.create(status = MemberStatus.BANNED)

                member.reinstate()

                member.status shouldBe MemberStatus.ACTIVE
            }

            "제재 상태가 아니면 해제할 수 없다" {
                val member = MemberFixture.create(status = MemberStatus.ACTIVE)

                val exception = shouldThrow<WarnException> {
                    member.reinstate()
                }

                exception.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
            }
        }

        "alignStatusWith" - {
            val now = LocalDateTime.of(2026, 7, 20, 12, 0)

            fun sanctionOf(member: Member, level: SanctionLevel, startsAt: LocalDateTime = now.minusDays(1)) =
                SanctionFixture.create(memberId = member.id, level = level, startsAt = startsAt)

            "남은 제재가 없으면 정지·차단을 풀어 ACTIVE로 되돌린다" {
                val member = MemberFixture.create(id = 1L, status = MemberStatus.BANNED)

                member.alignStatusWith(emptyList(), now)

                member.status shouldBe MemberStatus.ACTIVE
                member.suspendedUntil.shouldBeNull()
            }

            "남은 유효 제재 중 가장 무거운 것으로 맞춘다" {
                val member = MemberFixture.create(id = 1L, status = MemberStatus.SUSPENDED, suspendedUntil = until)
                val suspension = sanctionOf(member, SanctionLevel.SUSPENSION)
                val ban = sanctionOf(member, SanctionLevel.PERMANENT_BAN)

                member.alignStatusWith(listOf(suspension, ban), now)

                member.status shouldBe MemberStatus.BANNED
                member.suspendedUntil.shouldBeNull()
            }

            "차단이 사라지고 정지만 남으면 그 정지의 종료 시각으로 정지한다" {
                val member = MemberFixture.create(id = 1L, status = MemberStatus.BANNED)
                val suspension = sanctionOf(member, SanctionLevel.SUSPENSION)

                member.alignStatusWith(listOf(suspension), now)

                member.status shouldBe MemberStatus.SUSPENDED
                member.suspendedUntil shouldBe suspension.endsAt
            }

            "같은 정지가 여럿이면 가장 늦게 끝나는 정지로 맞춘다" {
                val member = MemberFixture.create(id = 1L, status = MemberStatus.BANNED)
                val endsSooner = SanctionFixture.create(memberId = member.id, startsAt = now, endsAt = now.plusDays(9))
                val endsLater = SanctionFixture.create(memberId = member.id, startsAt = now, endsAt = now.plusDays(13))

                member.alignStatusWith(listOf(endsLater, endsSooner), now)

                member.suspendedUntil shouldBe endsLater.endsAt
            }

            "다른 회원의 제재를 넘기면 호출 오류로 본다" {
                val member = MemberFixture.create(id = 1L, status = MemberStatus.BANNED)
                val othersBan = SanctionFixture.create(memberId = 2L, level = SanctionLevel.PERMANENT_BAN)

                shouldThrow<IllegalArgumentException> { member.alignStatusWith(listOf(othersBan), now) }
            }

            "경고와 기간이 지난 정지는 상태에 반영하지 않는다" {
                val member = MemberFixture.create(id = 1L, status = MemberStatus.SUSPENDED, suspendedUntil = until)
                val warning = sanctionOf(member, SanctionLevel.WARNING)
                val expiredSuspension = SanctionFixture.create(
                    memberId = member.id,
                    level = SanctionLevel.SUSPENSION,
                    startsAt = now.minusDays(30),
                    endsAt = now.minusDays(1),
                )

                member.alignStatusWith(listOf(warning, expiredSuspension), now)

                member.status shouldBe MemberStatus.ACTIVE
            }

            "정지·차단 중이 아니면 상태를 바꾸지 않는다" {
                val member = MemberFixture.create(id = 1L, status = MemberStatus.LEFT)

                member.alignStatusWith(listOf(sanctionOf(member, SanctionLevel.PERMANENT_BAN)), now)

                member.status shouldBe MemberStatus.LEFT
            }
        }

        "register" - {
            "PENDING이 아니면 가입 완료 전이를 거부한다 — 제재 회원의 ACTIVE 복귀 경로 봉쇄" {
                val member = MemberFixture.create(status = MemberStatus.BANNED)

                val exception = shouldThrow<WarnException> {
                    member.register(
                        name = null,
                        nickname = null,
                        phoneNumber = null,
                        gender = null,
                        age = null,
                        birthDate = null,
                        email = null,
                        interests = emptySet(),
                        location = Location.SEOUL,
                        job = Job.IT_TECH,
                        caricature = "caricature-1",
                    )
                }

                exception.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
            }
        }
    },
)
