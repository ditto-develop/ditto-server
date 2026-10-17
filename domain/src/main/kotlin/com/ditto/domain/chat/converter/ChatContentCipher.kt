package com.ditto.domain.chat.converter

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 채팅 본문을 AES-256-GCM 으로 암호화한다. 저장 형식은 버전 접두어 + Base64(IV + 암호문 + 태그)다. */
class ChatContentCipher(secretKey: ByteArray) {

    init {
        require(secretKey.size == KEY_BYTES) { "채팅 암호화 키는 ${KEY_BYTES}바이트여야 한다: ${secretKey.size}" }
    }

    private val key = SecretKeySpec(secretKey, ALGORITHM)
    private val random = SecureRandom()

    fun encrypt(plainText: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val encrypted = cipherOf(Cipher.ENCRYPT_MODE, iv).doFinal(plainText.toByteArray(Charsets.UTF_8))
        return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(encryptedText: String): String {
        require(isEncrypted(encryptedText)) { "암호화된 채팅 본문이 아니다" }
        return runCatching { decryptPayload(encryptedText.removePrefix(ENCRYPTED_PREFIX)) }
            .getOrElse {
                // 본문은 남기지 않는다.
                throw IllegalStateException(
                    "채팅 본문을 복호화하지 못했다. 저장할 때와 키가 다르거나 값이 손상됐다: 길이 ${encryptedText.length}",
                    it,
                )
            }
    }

    fun isEncrypted(storedText: String): Boolean = storedText.startsWith(ENCRYPTED_PREFIX)

    private fun decryptPayload(encodedPayload: String): String {
        val payload = Base64.getDecoder().decode(encodedPayload)
        val iv = payload.copyOfRange(0, IV_BYTES)
        val decrypted = cipherOf(Cipher.DECRYPT_MODE, iv).doFinal(payload, IV_BYTES, payload.size - IV_BYTES)
        return String(decrypted, Charsets.UTF_8)
    }

    // Cipher 는 스레드 안전하지 않아 호출마다 만든다.
    private fun cipherOf(mode: Int, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(mode, key, GCMParameterSpec(TAG_BITS, iv)) }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        // 키를 바꾸면 v2 를 붙여 옛 키로 쓴 행과 구분한다.
        const val ENCRYPTED_PREFIX = "enc:v1:"

        fun fromBase64Key(base64Key: String): ChatContentCipher {
            val secretKey = runCatching { Base64.getDecoder().decode(base64Key.trim()) }
                .getOrElse {
                    throw IllegalArgumentException("채팅 암호화 키가 Base64 가 아니다. 비밀값을 평문 한 줄로 넣었는지 확인한다", it)
                }
            return ChatContentCipher(secretKey)
        }
    }
}
