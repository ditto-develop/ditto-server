package com.ditto.infrastructure.oauth.apple

import io.jsonwebtoken.Jwts
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class AppleClientSecretGeneratorTest : FreeSpec(
    {
        val keyPair: KeyPair = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        val base64Key = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n${base64Key.chunked(64).joinToString("\n")}\n-----END PRIVATE KEY-----"

        fun generator(privateKey: String = pem) = AppleClientSecretGenerator(
            AppleOAuthProperties(teamId = "TEAM123456", keyId = "KEY1234567", privateKey = privateKey),
        )

        fun parse(secret: String) = Jwts.parser().verifyWith(keyPair.public).build().parseSignedClaims(secret)

        "팀 키로 서명하고 애플이 요구하는 클레임을 담는다" {
            val secret = generator().generate("pics.ditto.app")

            val jws = parse(secret)
            jws.header.keyId shouldBe "KEY1234567"
            jws.header.algorithm shouldBe "ES256"
            jws.payload.issuer shouldBe "TEAM123456"
            jws.payload.subject shouldBe "pics.ditto.app"
            jws.payload.audience shouldBe setOf(AppleOAuthProperties.ISSUER)
            val lifetimeSeconds = (jws.payload.expiration.time - jws.payload.issuedAt.time) / 1000
            lifetimeSeconds shouldBeLessThanOrEqual 300
        }

        "환경변수로 넣으며 줄바꿈이 \\n 문자로 바뀐 키도 읽는다" {
            val escaped = pem.replace("\n", "\\n")

            val secret = generator(privateKey = escaped).generate("pics.ditto.web")

            parse(secret).payload.subject shouldBe "pics.ditto.web"
        }
    },
)
