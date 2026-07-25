package com.ditto.infrastructure.storage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class FakeObjectStorageTest : FreeSpec({

    "발급한 키는 업로드된 것으로 간주한다" {
        val storage = FakeObjectStorage()

        val url = storage.issueUploadUrl("pending/key", "image/png", 1024L)

        url shouldContain "pending/key"
        storage.exists("pending/key") shouldBe true
    }

    "발급하지 않은 키는 존재하지 않는다" {
        FakeObjectStorage().exists("unknown-key") shouldBe false
    }

    "move하면 원본은 사라지고 대상 키가 존재한다" {
        val storage = FakeObjectStorage()
        storage.issueUploadUrl("pending/key", "image/png", 1024L)

        storage.move("pending/key", "user-reports/key")

        storage.exists("pending/key") shouldBe false
        storage.exists("user-reports/key") shouldBe true
    }

    "존재하지 않는 키의 move는 실패한다" {
        shouldThrow<IllegalStateException> {
            FakeObjectStorage().move("unknown-key", "target-key")
        }
    }
})
