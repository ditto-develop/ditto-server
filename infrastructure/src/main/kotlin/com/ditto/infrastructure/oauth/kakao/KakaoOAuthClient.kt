package com.ditto.infrastructure.oauth.kakao

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
                birthdayType = kakaoAccount?.birthdayType,
            ),
        )
    }

    /**
     * 카카오의 birthyear(YYYY) + birthday(MMDD)를 LocalDate로 합친다.
     * - 둘 중 하나라도 없으면(부분 동의) null
     * - 음력(LUNAR) 생일은 양력 변환을 지원하지 않으므로 null (FE에서 직접 입력 fallback)
     * - 포맷이 잘못된 경우에도 로그인 자체는 성공해야 하므로 예외 없이 null + 경고 로그
     */
    private fun parseBirthDate(
        birthyear: String?,
        birthday: String?,
        birthdayType: String?,
    ): LocalDate? {
        if (birthyear.isNullOrBlank() || birthday.isNullOrBlank()) return null
        if (birthdayType != null && birthdayType != BIRTHDAY_TYPE_SOLAR) {
            log.error { "생년월일이 음력으로 되어있어 값을 채우지 않습니다." }
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
        private const val BIRTHDAY_TYPE_SOLAR = "SOLAR"

        // 카카오는 콤마로 구분된 scope 목록을 허용한다.
        private const val SCOPE_DELIMITER = ","
        private val SCOPES = listOf("account_email", "birthyear", "birthday")
    }
}
