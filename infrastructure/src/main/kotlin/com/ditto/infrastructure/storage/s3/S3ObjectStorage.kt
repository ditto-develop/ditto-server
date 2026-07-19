package com.ditto.infrastructure.storage.s3

import com.ditto.infrastructure.storage.ObjectStorage
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

class S3ObjectStorage(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: StorageProperties,
) : ObjectStorage {

    override fun issueUploadUrl(key: String, contentType: String, contentLength: Long): String {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(contentLength)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(properties.putUrlTtl)
            .putObjectRequest(putObjectRequest)
            .build()

        return s3Presigner.presignPutObject(presignRequest).url().toExternalForm()
    }

    override fun issueViewUrl(key: String): String {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(properties.viewUrlTtl)
            .getObjectRequest(getObjectRequest)
            .build()

        return s3Presigner.presignGetObject(presignRequest).url().toExternalForm()
    }

    override fun exists(key: String): Boolean {
        return runCatching {
            s3Client.headObject { it.bucket(properties.bucket).key(key) }
        }.fold(
            onSuccess = { true },
            onFailure = { exception ->
                if (exception is NoSuchKeyException) false else throw exception
            },
        )
    }

    override fun move(sourceKey: String, targetKey: String) {
        s3Client.copyObject {
            it.sourceBucket(properties.bucket)
                .sourceKey(sourceKey)
                .destinationBucket(properties.bucket)
                .destinationKey(targetKey)
        }
        s3Client.deleteObject {
            it.bucket(properties.bucket).key(sourceKey)
        }
    }
}
