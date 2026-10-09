package com.ditto.infrastructure.oauth.apple

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/** 테스트가 만든 RSA 키를 애플 JWKS 처럼 돌려준다. */
object AppleJwksFixture {

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    // Jwk 객체의 toString 은 JSON 이 아니라서 애플이 주는 형식 그대로 손으로 만든다.
    fun jwksJson(keyId: String, keyPair: KeyPair): String {
        val publicKey = keyPair.public as RSAPublicKey
        return """{"keys":[{"kty":"RSA","kid":"$keyId","use":"sig","alg":"RS256",""" +
            """"n":"${encode(publicKey.modulus)}","e":"${encode(publicKey.publicExponent)}"}]}"""
    }

    fun senderReturning(vararg responses: String): AppleJwksSender {
        val sender = mockk<AppleJwksSender>()
        every { sender.getKeys() } returnsMany responses.toList()
        return sender
    }

    private fun encode(value: BigInteger): String {
        val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
