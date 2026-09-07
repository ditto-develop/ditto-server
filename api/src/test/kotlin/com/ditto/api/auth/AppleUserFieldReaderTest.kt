package com.ditto.api.auth

import com.ditto.api.auth.service.AppleUserFieldReader
import com.ditto.common.serialization.ObjectMapperFactory
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * 애플 웹 콜백의 `user` 폼 필드는 **최초 인가 1회만** 오고, 형식이 어긋나도 로그인은 성공해야 한다.
 * 이름은 나중에 `PATCH /users/me/personal-info` 로도 채울 수 있으므로 여기서 막을 이유가 없다.
 */
class AppleUserFieldReaderTest : FreeSpec(
    {
        val reader = AppleUserFieldReader(ObjectMapperFactory.create())

        "이름 읽기" - {
            "성과 이름을 한국어 순서로 합친다" {
                val userField = """{"name":{"firstName":"철수","lastName":"김"},"email":"user@example.com"}"""

                reader.readName(userField) shouldBe "김철수"
            }

            "한쪽만 오면 그것만 쓴다" {
                reader.readName("""{"name":{"firstName":"철수"}}""") shouldBe "철수"
                reader.readName("""{"name":{"lastName":"김"}}""") shouldBe "김"
            }
        }

        "이름이 없는 경우" - {
            "필드 자체가 없으면(재로그인) null" {
                reader.readName(null) shouldBe null
            }

            "빈 문자열이면 null" {
                reader.readName("") shouldBe null
            }

            "name 객체가 없으면 null" {
                reader.readName("""{"email":"user@example.com"}""") shouldBe null
            }

            "이름 값이 공백뿐이면 null" {
                reader.readName("""{"name":{"firstName":" ","lastName":" "}}""") shouldBe null
            }
        }

        "형식이 어긋나도 로그인을 막지 않는다" - {
            "JSON 이 아니면 null 을 주고 넘어간다" {
                reader.readName("not-json") shouldBe null
            }

            "name 이 객체가 아니어도 null 을 주고 넘어간다" {
                reader.readName("""{"name":"철수"}""") shouldBe null
            }
        }
    },
)
