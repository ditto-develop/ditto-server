package com.ditto.infrastructure.storage.config

import com.ditto.infrastructure.storage.FakeObjectStorage
import com.ditto.infrastructure.storage.ObjectStorage
import com.ditto.infrastructure.storage.s3.S3ObjectStorage
import com.ditto.infrastructure.storage.s3.StorageProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
@EnableConfigurationProperties(
    StorageProperties::class,
)
class StorageConfig {

    @Profile("local", "test")
    @Configuration
    inner class FakeStorageConfig {

        @Bean
        fun objectStorage(): ObjectStorage = FakeObjectStorage()
    }

    @Profile("prod")
    @Configuration
    inner class S3StorageConfig {

        @Bean
        fun s3Client(properties: StorageProperties): S3Client =
            S3Client.builder()
                .region(Region.of(properties.region))
                .build()

        @Bean
        fun s3Presigner(properties: StorageProperties): S3Presigner =
            S3Presigner.builder()
                .region(Region.of(properties.region))
                .build()

        @Bean
        fun objectStorage(
            s3Client: S3Client,
            s3Presigner: S3Presigner,
            properties: StorageProperties,
        ): ObjectStorage = S3ObjectStorage(s3Client, s3Presigner, properties)
    }
}
