package com.ditto.api.auth.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 애플 웹 콜백의 `user` 폼 필드를 읽는다.
 *
 * 애플은 **최초 인가 1회만** 이름을 주고, ID 토큰이 아니라 이 필드에 JSON 문자열로 싣는다.
 * ```json
 * {"name":{"firstName":"철수","lastName":"김"},"email":"..."}
 * ```
 * 재로그인 때는 필드 자체가 없다. 파싱에 실패해도 로그인은 성공해야 하므로 예외 없이 null 을 준다 —
 * 이름은 나중에 `PATCH /api/v1/users/me/personal-info` 로도 채울 수 있다.
 */
@Component
class AppleUserFieldReader(
    private val objectMapper: ObjectMapper,
) {
    fun readName(userField: String?): String? {
        if (userField.isNullOrBlank()) return null

        return runCatching {
            val name = objectMapper.readTree(userField).path("name")
            // 한국어 표기 순서(성 + 이름)로 합친다. 한쪽만 와도 그것만 쓴다.
            val fullName = listOfNotNull(
                name.path("lastName").asText(null),
                name.path("firstName").asText(null),
            ).filter { it.isNotBlank() }.joinToString("")

            fullName.ifBlank { null }
        }.getOrElse {
            log.warn { "애플 user 필드 파싱 실패 — 이름 없이 로그인을 진행한다." }
            null
        }
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
