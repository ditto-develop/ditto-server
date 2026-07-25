package com.ditto.infrastructure.storage.s3

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URI
import java.time.Duration
import java.util.function.Consumer
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.CopyObjectResponse
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

class S3ObjectStorageTest : FreeSpec({

    val properties = StorageProperties(
        bucket = "test-bucket",
        region = "ap-northeast-2",
        putUrlTtl = Duration.ofMinutes(10),
    )

    fun storage(s3Client: S3Client = mockk(), s3Presigner: S3Presigner = mockk()) =
        S3ObjectStorage(s3Client, s3Presigner, properties)

    "issueUploadUrl" - {
        "presigner가 서명한 업로드 URL을 반환한다" {
            val s3Presigner = mockk<S3Presigner>()
            val presigned = mockk<PresignedPutObjectRequest>()
            every { presigned.url() } returns URI.create("https://test-bucket.s3.amazonaws.com/key?sig=abc").toURL()
            every { s3Presigner.presignPutObject(any<PutObjectPresignRequest>()) } returns presigned

            val url = storage(s3Presigner = s3Presigner).issueUploadUrl("pending/key", "image/png", 1024L)

            url shouldBe "https://test-bucket.s3.amazonaws.com/key?sig=abc"
        }
    }

    "exists" - {
        "객체가 있으면 true를 반환한다" {
            val s3Client = mockk<S3Client>()
            every { s3Client.headObject(any<Consumer<HeadObjectRequest.Builder>>()) } returns
                HeadObjectResponse.builder().build()

            storage(s3Client = s3Client).exists("pending/key") shouldBe true
        }

        "객체가 없으면 false를 반환한다" {
            val s3Client = mockk<S3Client>()
            every { s3Client.headObject(any<Consumer<HeadObjectRequest.Builder>>()) } throws
                NoSuchKeyException.builder().build()

            storage(s3Client = s3Client).exists("pending/missing") shouldBe false
        }

        "그 외 실패는 그대로 전파한다" {
            val s3Client = mockk<S3Client>()
            every { s3Client.headObject(any<Consumer<HeadObjectRequest.Builder>>()) } throws
                AwsServiceException.builder().message("s3 오류").build()

            shouldThrow<AwsServiceException> {
                storage(s3Client = s3Client).exists("pending/key")
            }
        }
    }

    "move" - {
        "복사 후 원본을 삭제한다" {
            val s3Client = mockk<S3Client>()
            every { s3Client.copyObject(any<Consumer<CopyObjectRequest.Builder>>()) } returns
                CopyObjectResponse.builder().build()
            every { s3Client.deleteObject(any<Consumer<DeleteObjectRequest.Builder>>()) } returns mockk()

            storage(s3Client = s3Client).move("pending/key", "user-reports/key")

            verify(exactly = 1) { s3Client.copyObject(any<Consumer<CopyObjectRequest.Builder>>()) }
            verify(exactly = 1) { s3Client.deleteObject(any<Consumer<DeleteObjectRequest.Builder>>()) }
        }
    }
})
