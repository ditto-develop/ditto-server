package com.ditto.domain.notification.entity

import com.ditto.domain.notification.SystemNoticeFixture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SystemNoticeTest : FreeSpec({

    "만들 때는 수신 수가 0 이고, 발송이 끝나면 적재된 수를 남긴다" {
        val notice = SystemNoticeFixture.create()
        notice.recipientCount shouldBe 0

        notice.recordRecipientCount(120)

        notice.recipientCount shouldBe 120
    }
})
