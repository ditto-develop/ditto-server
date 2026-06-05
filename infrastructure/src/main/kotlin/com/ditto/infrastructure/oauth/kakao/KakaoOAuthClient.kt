package com.ditto.infrastructure.oauth.kakao

import com.ditto.domain.member.entity.Gender
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo
import com.ditto.infrastructure.oauth.constants.OAuthConstants
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.time.LocalDate

class KakaoOAuthClient(
    private val properties: KakaoOAuthProperties,
    private val client: KakaoApiSender,
) : OAuthClient {

    override fun getAuthorizationUrl(): String {
        return "${AUTHORIZATION_URI}?" +
            "${OAuthConstants.PARAM_CLIENT_ID}=${properties.clientId}" +
            "&${OAuthConstants.PARAM_REDIRECT_URI}=${properties.redirectUri}" +
            "&${OAuthConstants.PARAM_RESPONSE_TYPE}=${OAuthConstants.RESPONSE_TYPE_CODE}" +
            "&${OAuthConstants.PARAM_SCOPE}=${SCOPES.joinToString(SCOPE_DELIMITER)}"
    }

    override fun getAccessToken(code: String): String {
        val params = buildTokenRequestParams(code)

        return client.getToken(params).accessToken
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val response = client.getUserInfo("Bearer $accessToken")
        val kakaoAccount = response.kakaoAccount

        return OAuthUserInfo(
            id = response.id.toString(),
            nickname = kakaoAccount?.profile?.nickname ?: OAuthConstants.DEFAULT_NICKNAME,
            email = kakaoAccount?.email,
            birthDate = parseBirthDate(
                birthyear = kakaoAccount?.birthyear,
                birthday = kakaoAccount?.birthday,
            ),
            name = kakaoAccount?.name,
            phoneNumber = formatPhoneNumber(kakaoAccount?.phoneNumber),
            gender = parseGender(kakaoAccount?.gender),
        )
    }

    /**
     * 카카오 전화번호("+82 10-1234-5678" 등)를 국내 표준 "010-XXXX-XXXX" 포맷으로 변환한다.
     * - 숫자만 추출 → 국가코드(82) 제거 → 선두 0 보정 → 11자리면 하이픈 포맷
     * - 미동의/비정상 포맷이면 로그인 자체는 성공해야 하므로 예외 없이 null + 경고 로그
     */
    private fun formatPhoneNumber(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null

        var digits = phoneNumber.filter { it.isDigit() }
        if (digits.startsWith(KOREA_COUNTRY_CODE)) {
            digits = digits.removePrefix(KOREA_COUNTRY_CODE)
        }
        if (!digits.startsWith("0")) {
            digits = "0$digits"
        }

        if (digits.length != PHONE_NUMBER_LENGTH) {
            log.warn { "카카오 전화번호 포맷 비정상(11자리 아님): $phoneNumber" }
            return null
        }

        return "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
    }

    /**
     * 카카오 성별("male"/"female")을 Gender enum으로 변환한다.
     * - 미동의/알 수 없는 값이면 예외 없이 null + 경고 로그
     */
    private fun parseGender(gender: String?): Gender? {
        if (gender.isNullOrBlank()) return null

        return when (gender.lowercase()) {
            KAKAO_GENDER_MALE -> Gender.MALE
            KAKAO_GENDER_FEMALE -> Gender.FEMALE
            else -> {
                log.warn { "카카오 성별 값 알 수 없음: $gender" }
                null
            }
        }
    }

    /**
     * 카카오의 birthyear(YYYY) + birthday(MMDD)를 LocalDate로 합친다.
     * - 둘 중 하나라도 없으면(부분 동의) null
     * - 음력/양력(birthday_type) 구분 없이 받은 연·월·일을 그대로 저장한다.
     * - 포맷이 잘못된 경우에도 로그인 자체는 성공해야 하므로 예외 없이 null + 경고 로그
     */
    private fun parseBirthDate(
        birthyear: String?,
        birthday: String?,
    ): LocalDate? {
        if (birthyear.isNullOrBlank() || birthday.isNullOrBlank()) return null
        if (birthday.length != BIRTHDAY_LENGTH) {
            log.warn { "카카오 birthday 포맷 비정상(MMDD 아님): $birthday" }
            return null
        }

        return runCatching {
            LocalDate.of(
                birthyear.toInt(),
                birthday.substring(0, 2).toInt(),
                birthday.substring(2, 4).toInt(),
            )
        }.getOrElse {
            log.warn { "카카오 생년월일 파싱 실패: birthyear=$birthyear, birthday=$birthday" }
            null
        }
    }

    private fun buildTokenRequestParams(code: String): MultiValueMap<String, String> {
        val params = LinkedMultiValueMap<String, String>()

        params.add(OAuthConstants.PARAM_GRANT_TYPE, OAuthConstants.GRANT_TYPE_AUTHORIZATION_CODE)
        params.add(OAuthConstants.PARAM_CLIENT_ID, properties.clientId)
        params.add(OAuthConstants.PARAM_REDIRECT_URI, properties.redirectUri)
        params.add(OAuthConstants.PARAM_CODE, code)

        if (properties.clientSecret.isNotBlank()) {
            params.add(OAuthConstants.PARAM_CLIENT_SECRET, properties.clientSecret)
        }

        return params
    }

    companion object {
        private val log = KotlinLogging.logger {}
        private const val AUTHORIZATION_URI = "https://kauth.kakao.com/oauth/authorize"
        private const val BIRTHDAY_LENGTH = 4 // MMDD

        private const val KOREA_COUNTRY_CODE = "82"
        private const val PHONE_NUMBER_LENGTH = 11 // 01012345678
        private const val KAKAO_GENDER_MALE = "male"
        private const val KAKAO_GENDER_FEMALE = "female"

        // 카카오는 콤마로 구분된 scope 목록을 허용한다.
        private const val SCOPE_DELIMITER = ","
        private val SCOPES = listOf("account_email", "birthyear", "birthday", "name", "phone_number", "gender")
    }
}
