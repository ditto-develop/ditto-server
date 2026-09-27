package com.ditto.domain.member.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

private val NOW = LocalDateTime.of(2026, 9, 28, 12, 0)

class MemberNicknameChangeTest : FreeSpec(
    {
        "changeNickname" - {
            "처음 바꾸면 한 번이 남는다" {
                val member = MemberFixture.create(nickname = "처음", status = MemberStatus.ACTIVE)

                member.changeNickname("두번째", NOW)

                member.nickname shouldBe "두번째"
                member.remainingNicknameChanges(NOW) shouldBe 1
                member.nicknameChangeLockedUntilAt(NOW).shouldBeNull()
            }

            "두 번 바꾸면 14일 동안 잠긴다" {
                val member = MemberFixture.create(nickname = "처음", status = MemberStatus.ACTIVE)

                member.changeNickname("두번째", NOW)
                member.changeNickname("세번째", NOW.plusHours(1))

                member.remainingNicknameChanges(NOW.plusHours(1)) shouldBe 0
                member.nicknameChangeLockedUntilAt(NOW.plusHours(1)) shouldBe NOW.plusHours(1).plusDays(14)
                shouldThrow<WarnException> {
                    member.changeNickname("네번째", NOW.plusDays(14))
                }.errorCode shouldBe ErrorCode.NICKNAME_CHANGE_LOCKED
                member.nickname shouldBe "세번째"
            }

            "잠금이 풀리면 다시 두 번을 쓸 수 있다" {
                val member = MemberFixture.create(nickname = "처음", status = MemberStatus.ACTIVE)
                member.changeNickname("두번째", NOW)
                member.changeNickname("세번째", NOW)
                val unlocked = NOW.plusDays(14)

                member.remainingNicknameChanges(unlocked) shouldBe 2
                member.nicknameChangeLockedUntilAt(unlocked).shouldBeNull()

                member.changeNickname("네번째", unlocked)

                member.nickname shouldBe "네번째"
                member.remainingNicknameChanges(unlocked) shouldBe 1
            }

            "지금과 같은 닉네임은 변경으로 세지 않는다" {
                val member = MemberFixture.create(nickname = "처음", status = MemberStatus.ACTIVE)

                member.changeNickname("처음", NOW)

                member.remainingNicknameChanges(NOW) shouldBe 2
            }
        }
    },
)
