package com.ditto.api.support.image

import com.ditto.infrastructure.storage.ObjectStorage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PendingImageStorageTest {

    private val objectStorage = mockk<ObjectStorage>()
    private val pendingImageStorage = PendingImageStorage(objectStorage)

    @Test
    @DisplayName("이동 도중 실패하면 이미 옮긴 이미지를 pending으로 되돌리고 원래 예외를 던진다")
    fun restoreMovedImagesWhenMoveFails() {
        justRun { objectStorage.move("pending/feedbacks/1/a", "feedbacks/1/a") }
        every { objectStorage.move("pending/feedbacks/1/b", "feedbacks/1/b") } throws IllegalStateException("S3 장애")
        justRun { objectStorage.move("feedbacks/1/a", "pending/feedbacks/1/a") }

        val exception = shouldThrow<IllegalStateException> {
            pendingImageStorage.moveToPermanent(listOf("pending/feedbacks/1/a", "pending/feedbacks/1/b"))
        }

        exception.message shouldBe "S3 장애"
        verify(exactly = 1) { objectStorage.move("feedbacks/1/a", "pending/feedbacks/1/a") }
    }

    @Test
    @DisplayName("되돌리기까지 실패해도 원래 예외를 던진다")
    fun throwOriginalExceptionWhenRestoreFails() {
        justRun { objectStorage.move("pending/feedbacks/1/a", "feedbacks/1/a") }
        every { objectStorage.move("pending/feedbacks/1/b", "feedbacks/1/b") } throws IllegalStateException("S3 장애")
        every { objectStorage.move("feedbacks/1/a", "pending/feedbacks/1/a") } throws IllegalStateException("되돌리기 실패")

        val exception = shouldThrow<IllegalStateException> {
            pendingImageStorage.moveToPermanent(listOf("pending/feedbacks/1/a", "pending/feedbacks/1/b"))
        }

        exception.message shouldBe "S3 장애"
    }
}
