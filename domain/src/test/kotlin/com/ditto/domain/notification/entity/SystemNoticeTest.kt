package com.ditto.domain.notification.entity

import com.ditto.common.exception.WarnException
import com.ditto.domain.notification.SystemNoticeFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SystemNoticeTest : FreeSpec({

    "만들 때는 수신 수가 0 이고, 발송이 끝나면 적재된 수를 남긴다" {
        val notice = SystemNoticeFixture.create()
        notice.recipientCount shouldBe 0

        notice.recordRecipientCount(120)

        notice.recipientCount shouldBe 120
    }

    "문구 검증" - {
        "앞뒤 공백을 지우고, 비어진 본문은 null 로 둔다" {
            val notice = SystemNoticeFixture.create(title = "  공지  ", body = "   ")

            notice.title shouldBe "공지"
            notice.body shouldBe null
        }

        // 브라우저 폼은 textarea 줄바꿈을 CRLF 로 보낸다. 화면 maxlength 를 통과한 500자가 거부되면 안 된다.
        "줄바꿈은 LF 로 맞춘 뒤 길이를 센다" {
            val body = ("가".repeat(98) + "\r\n").repeat(5)

            val notice = SystemNoticeFixture.create(body = body)

            notice.body shouldBe ("가".repeat(98) + "\n").repeat(4) + "가".repeat(98)
        }

        "제목이 비어 있으면 거부한다" {
            shouldThrow<WarnException> { SystemNoticeFixture.create(title = "   ") }
        }

        "제목이 100자를 넘으면 거부한다" {
            shouldThrow<WarnException> { SystemNoticeFixture.create(title = "가".repeat(101)) }
            SystemNoticeFixture.create(title = "가".repeat(100)).title.length shouldBe 100
        }

        "본문이 500자를 넘으면 거부한다" {
            shouldThrow<WarnException> { SystemNoticeFixture.create(body = "가".repeat(501)) }
        }
    }
})
