package com.ditto.api.auth

import com.ditto.api.auth.dto.ReviewLoginRequest
import com.ditto.api.auth.service.ReviewLoginService
import com.ditto.api.config.ReviewLoginProperties
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * 심사용 데모 로그인은 공개 레포에 해시만 두고 켜는 기능이라, 실패는 모두 같은 코드여야 한다 —
 * 어느 값이 틀렸는지나 기능이 켜져 있는지를 응답으로 알려 주지 않는다.
 */
class ReviewLoginServiceTest : FreeSpec(
    {
        val passwordHash = BCryptPasswordEncoder(4).encode("review-password")
        val service = ReviewLoginService(ReviewLoginProperties(loginId = "app-review", passwordHash = passwordHash))

        fun shouldReject(block: () -> Unit) {
            shouldThrow<WarnException>(block).errorCode shouldBe ErrorCode.INVALID_REVIEW_LOGIN
        }

        "ID와 비밀번호가 맞으면 통과한다" {
            shouldNotThrowAny {
                service.authenticate(ReviewLoginRequest(loginId = "app-review", password = "review-password"))
            }
        }

        "비밀번호가 틀리면 거부한다" {
            shouldReject {
                service.authenticate(ReviewLoginRequest(loginId = "app-review", password = "wrong-password"))
            }
        }

        "ID가 틀리면 비밀번호가 맞아도 거부한다" {
            shouldReject {
                service.authenticate(ReviewLoginRequest(loginId = "someone", password = "review-password"))
            }
        }

        "설정이 비어 있으면 꺼져 있어 무엇을 보내도 거부한다" {
            val disabled = ReviewLoginService(ReviewLoginProperties(loginId = "", passwordHash = ""))

            shouldReject {
                disabled.authenticate(ReviewLoginRequest(loginId = "", password = "review-password"))
            }
        }

        "해시 형식이 잘못 설정되어 있어도 서버 오류가 아니라 거부로 끝난다" {
            val misconfigured = ReviewLoginService(
                ReviewLoginProperties(loginId = "app-review", passwordHash = "review-password"),
            )

            shouldReject {
                misconfigured.authenticate(ReviewLoginRequest(loginId = "app-review", password = "review-password"))
            }
        }
    },
)
