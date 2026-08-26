package com.ditto.infrastructure.fcm.config

import com.ditto.infrastructure.fcm.FakePushSender
import com.ditto.infrastructure.fcm.PushSender
import com.ditto.infrastructure.fcm.firebase.FcmProperties
import com.ditto.infrastructure.fcm.firebase.FirebaseFcmSender
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class FcmConfig {

    @Profile("local", "test")
    @Configuration
    inner class FakeFcmConfig {

        @Bean
        fun pushSender(): PushSender = FakePushSender()
    }

    /** 키(DITTO_FCM_CREDENTIALS) 미주입이면 바인딩 실패로 부팅이 죽는다 — 설정 누락은 배포 시점에 드러나야 한다. */
    @Profile("prod")
    @Configuration
    @EnableConfigurationProperties(FcmProperties::class)
    inner class FirebaseFcmConfig {

        @Bean
        fun firebaseApp(properties: FcmProperties): FirebaseApp {
            val credentials = properties.credentials.byteInputStream().use { GoogleCredentials.fromStream(it) }
            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build()
            return FirebaseApp.initializeApp(options)
        }

        @Bean
        fun firebaseMessaging(firebaseApp: FirebaseApp): FirebaseMessaging = FirebaseMessaging.getInstance(firebaseApp)

        @Bean
        fun pushSender(firebaseMessaging: FirebaseMessaging): PushSender = FirebaseFcmSender(firebaseMessaging)
    }
}
