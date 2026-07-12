package com.ditto.domain.sanction.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.sanction.SanctionFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class SanctionTest : FreeSpec(
    {
        val startsAt = LocalDateTime.of(2026, 7, 13, 0, 0)

        "impose" - {
            "기간 제재는 시작·종료 일시와 함께 ACTIVE로 생성된다" {
                val sanction = SanctionFixture.create(
                    level = SanctionLevel.SUSPENSION,
                    startsAt = startsAt,
                    endsAt = startsAt.plusDays(14),
                )

                sanction.status shouldBe SanctionStatus.ACTIVE
                sanction.endsAt shouldBe startsAt.plusDays(14)
            }

            "영구 차단은 종료 일시가 없어야 한다" {
                val exception = shouldThrow<WarnException> {
                    SanctionFixture.create(
                        level = SanctionLevel.PERMANENT_BAN,
                        startsAt = startsAt,
                        endsAt = startsAt.plusDays(1),
                    )
                }

                exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            }

            "기간 제재에 종료 일시가 없으면 거부한다" {
                val exception = shouldThrow<WarnException> {
                    SanctionFixture.create(level = SanctionLevel.WARNING, endsAt = null)
                }

                exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            }

            "종료 일시가 시작 일시보다 이르거나 같으면 거부한다" {
                val exception = shouldThrow<WarnException> {
                    SanctionFixture.create(startsAt = startsAt, endsAt = startsAt)
                }

                exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            }
        }

        "상태 전이" - {
            "expire로 기간 만료 종결한다" {
                val sanction = SanctionFixture.create()

                sanction.expire()

                sanction.status shouldBe SanctionStatus.EXPIRED
            }

            "lift로 직권 해제 종결한다" {
                val sanction = SanctionFixture.create()

                sanction.lift()

                sanction.status shouldBe SanctionStatus.LIFTED
            }

            "종결된 제재는 다시 전이할 수 없다" {
                val sanction = SanctionFixture.create()
                sanction.lift()

                val exception = shouldThrow<WarnException> {
                    sanction.expire()
                }

                exception.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
            }
        }
    },
)
