package com.ditto.domain.chat.converter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import java.util.Base64
import javax.crypto.AEADBadTagException

class ChatContentCipherTest : FreeSpec({

    val cipher = ChatContentCipher(ByteArray(32) { it.toByte() })

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

    "다른 키로는 복호화하지 못한다" {
        val otherCipher = ChatContentCipher(ByteArray(32) { 1 })

        shouldThrow<AEADBadTagException> { otherCipher.decrypt(cipher.encrypt("안녕하세요")) }
    }

    "암호문이 바뀌면 복호화하지 못한다" {
        val payload = Base64.getDecoder().decode(cipher.encrypt("안녕하세요").removePrefix(ChatContentCipher.ENCRYPTED_PREFIX))
        payload[payload.size / 2] = (payload[payload.size / 2] + 1).toByte()
        val tampered = ChatContentCipher.ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload)

        shouldThrow<AEADBadTagException> { cipher.decrypt(tampered) }
    }

    "평문은 복호화하지 않는다" {
        shouldThrow<IllegalArgumentException> { cipher.decrypt("안녕하세요") }
    }

    "키가 32바이트가 아니면 만들지 않는다" {
        shouldThrow<IllegalArgumentException> { ChatContentCipher(ByteArray(16)) }
    }

    "Base64 키로 만들 수 있다" {
        val fromBase64 = ChatContentCipher.fromBase64Key("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")

        fromBase64.decrypt(cipher.encrypt("안녕하세요")) shouldBe "안녕하세요"
    }
})
