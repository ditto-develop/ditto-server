package com.ditto.domain.chat.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * 채팅 본문을 암호화해 저장한다. 암호화를 도입하기 전에 쌓인 평문 행은 그대로 읽는다.
 * 하이버네이트가 스프링 빈으로 만들기 때문에 암호기 빈을 생성자로 받는다.
 */
@Converter
class ChatContentConverter(
    private val cipher: ChatContentCipher,
) : AttributeConverter<String, String> {

    override fun convertToDatabaseColumn(attribute: String?): String? = attribute?.let(cipher::encrypt)

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null || !cipher.isEncrypted(dbData)) {
            return dbData
        }
        return cipher.decrypt(dbData)
    }
}
