package com.ditto.domain.chat.converter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Base64
import javax.crypto.AEADBadTagException

class ChatContentCipherTest : FreeSpec({

    val keyBytes = ByteArray(32) { it.toByte() }
    val cipher = ChatContentCipher(keyBytes)

    "암호화한 본문은 복호화하면 원문으로 돌아온다" {
        val encrypted = cipher.encrypt("안녕하세요 😀")

        encrypted shouldStartWith ChatContentCipher.ENCRYPTED_PREFIX
        encrypted shouldNotContain "안녕하세요"
        cipher.decrypt(encrypted) shouldBe "안녕하세요 😀"
    }

    "같은 본문도 암호화할 때마다 다른 값이 된다" {
        cipher.encrypt("안녕하세요") shouldNotBe cipher.encrypt("안녕하세요")
    }

    "접두어가 있는 값만 암호화된 것으로 본다" {
        cipher.isEncrypted(cipher.encrypt("안녕하세요")) shouldBe true
        cipher.isEncrypted("안녕하세요") shouldBe false
    }

    "다른 키로 복호화하면 원인을 알리는 예외가 난다" {
        val otherCipher = ChatContentCipher(ByteArray(32) { 1 })

        val exception = shouldThrow<IllegalStateException> { otherCipher.decrypt(cipher.encrypt("안녕하세요")) }

        exception.cause.shouldBeInstanceOf<AEADBadTagException>()
    }

    "암호문이 바뀌면 복호화하지 못한다" {
        val encoded = cipher.encrypt("안녕하세요").removePrefix(ChatContentCipher.ENCRYPTED_PREFIX)
        val payload = Base64.getDecoder().decode(encoded)
        val middle = payload.size / 2
        payload[middle] = payload[middle].inc()
        val tampered = ChatContentCipher.ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload)

        shouldThrow<IllegalStateException> { cipher.decrypt(tampered) }
    }

    "접두어만 같고 Base64 가 아닌 값도 복호화 실패로 알린다" {
        shouldThrow<IllegalStateException> { cipher.decrypt("${ChatContentCipher.ENCRYPTED_PREFIX}안녕") }
    }

    "접두어 없는 평문을 복호화하면 예외가 난다" {
        shouldThrow<IllegalArgumentException> { cipher.decrypt("안녕하세요") }
    }

    "키가 32바이트가 아니면 만들지 않는다" {
        shouldThrow<IllegalArgumentException> { ChatContentCipher(ByteArray(16)) }
    }

    "Base64 로 인코딩한 키는 같은 바이트 키와 같은 암호기가 된다" {
        val base64Key = Base64.getEncoder().encodeToString(keyBytes)

        val fromBase64 = ChatContentCipher.fromBase64Key("$base64Key\n")

        fromBase64.decrypt(cipher.encrypt("안녕하세요")) shouldBe "안녕하세요"
    }

    "Base64 가 아닌 키는 만들지 않는다" {
        shouldThrow<IllegalArgumentException> { ChatContentCipher.fromBase64Key("{\"key\":\"value\"}") }
    }
})
