package com.ditto.domain.member.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class MemberLeaveTransitionTest : FreeSpec(
    {
        val leftAt = LocalDateTime.of(2026, 8, 1, 12, 0)

        "leave" - {
            "탈퇴하면 LEFT가 되고 일시·사유가 남는다" {
                val member = MemberFixture.create(status = MemberStatus.ACTIVE)

                member.leave(reason = "not-useful", now = leftAt)

                member.status shouldBe MemberStatus.LEFT
                member.leftAt shouldBe leftAt
                member.leaveReason shouldBe "not-useful"
                member.isLeft() shouldBe true
            }

            "사유 없이도 탈퇴할 수 있다" {
                val member = MemberFixture.create(status = MemberStatus.ACTIVE)

                member.leave(reason = null, now = leftAt)

                member.status shouldBe MemberStatus.LEFT
                member.leaveReason.shouldBeNull()
            }

            "제재 중인 회원도 탈퇴할 수 있다 — 소프트 삭제는 제재 이력을 보존한다" {
                val banned = MemberFixture.create(status = MemberStatus.BANNED)

                banned.leave(reason = "etc", now = leftAt)

                banned.status shouldBe MemberStatus.LEFT
            }

            "이미 탈퇴한 회원은 다시 탈퇴할 수 없다 — 최초 탈퇴 시각이 덮이면 복구 기한이 어긋난다" {
                val member = MemberFixture.create(status = MemberStatus.ACTIVE)
                member.leave(reason = "etc", now = leftAt)

                val exception = shouldThrow<WarnException> {
                    member.leave(reason = "etc", now = leftAt.plusDays(1))
                }

                exception.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                member.leftAt shouldBe leftAt
            }
        }

        "restore" - {
            "복구하면 ACTIVE로 돌아오고 탈퇴 기록이 비워진다" {
                val member = MemberFixture.create(status = MemberStatus.ACTIVE)
                member.leave(reason = "etc", now = leftAt)

                member.restore()

                member.status shouldBe MemberStatus.ACTIVE
                member.leftAt.shouldBeNull()
                member.leaveReason.shouldBeNull()
            }

            "탈퇴하지 않은 회원은 복구할 수 없다" {
                val member = MemberFixture.create(status = MemberStatus.ACTIVE)

                val exception = shouldThrow<WarnException> {
                    member.restore()
                }

                exception.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
            }
        }

        "isRetentionExpiredAt" - {
            "보존 기간이 지나면 참이다" {
                val member = MemberFixture.create(status = MemberStatus.ACTIVE)
                member.leave(reason = "etc", now = leftAt)

                member.isRetentionExpiredAt(leftAt.plusDays(30), retentionDays = 30) shouldBe true
            }

            "경계(정확히 30일)는 만료로 본다" {
                val member = MemberFixture.create(status = MemberStatus.ACTIVE)
                member.leave(reason = "etc", now = leftAt)

                member.isRetentionExpiredAt(leftAt.plusDays(30).minusSeconds(1), retentionDays = 30) shouldBe false
            }

            "탈퇴하지 않은 회원은 만료 대상이 아니다" {
                val member = MemberFixture.create(status = MemberStatus.ACTIVE)

                member.isRetentionExpiredAt(leftAt.plusYears(1), retentionDays = 30) shouldBe false
            }
        }
    },
)
