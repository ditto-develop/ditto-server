package com.ditto.api.system.featureflag

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * `GET /api/v1/feature-flags?keys=...` 로 받은 키 목록을 다듬는다. 공백을 지우고 중복을 뺀다(처음 나온 순서 유지).
 * API Key 만으로 부르는 경로라 개수·길이·글자를 묶어 둔다 — 키는 enum 이름 모양(`[A-Z0-9_]`)만 받는다.
 */
object FeatureFlagKeyQuery {
    const val MAX_KEYS = 20
    const val MAX_KEY_LENGTH = 50
    private val KEY_PATTERN = Regex("^[A-Z0-9_]+$")

    fun normalize(rawKeys: List<String>?): List<String> {
        val keys = rawKeys.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (keys.isEmpty() || keys.size > MAX_KEYS) throw WarnException(ErrorCode.BAD_REQUEST)
        if (keys.any { it.length > MAX_KEY_LENGTH || !KEY_PATTERN.matches(it) }) throw WarnException(ErrorCode.BAD_REQUEST)
        return keys
    }
}
