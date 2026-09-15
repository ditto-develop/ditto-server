package com.ditto.infrastructure.oauth.kakao

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.common.serialization.ObjectMapperFactory
import com.ditto.domain.member.entity.Gender
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo
import com.ditto.infrastructure.oauth.constants.OAuthConstants
import com.ditto.infrastructure.oauth.kakao.dto.KakaoTokenErrorResponse
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import java.time.LocalDate

class KakaoOAuthClient(
    private val properties: KakaoOAuthProperties,
    private val client: KakaoApiSender,
) : OAuthClient {

    /**
     * 동의항목은 [KakaoOAuthProperties.scopes]가 정한다 — 설정되지 않은 동의항목을 넘기면 카카오가 로그인을
     * 거부하므로, 앱 종류(일반/비즈)에 따라 코드가 아니라 설정으로 맞춘다. 비어 있으면 scope 없이 요청한다.
     */
    override fun getAuthorizationUrl(): String {
        val base = "${AUTHORIZATION_URI}?" +
            "${OAuthConstants.PARAM_CLIENT_ID}=${properties.clientId}" +
            "&${OAuthConstants.PARAM_REDIRECT_URI}=${properties.redirectUri}" +
            "&${OAuthConstants.PARAM_RESPONSE_TYPE}=${OAuthConstants.RESPONSE_TYPE_CODE}"

        if (properties.scopes.isEmpty()) return base
        return base + "&${OAuthConstants.PARAM_SCOPE}=${properties.scopes.joinToString(SCOPE_DELIMITER)}"
    }

    override fun getAccessToken(code: String): String {
        val params = buildTokenRequestParams(code)
        val response = try {
            client.getToken(params)
        } catch (e: RestClientException) {
            throw translateTokenExchangeFailure(e)
        }

        return response.accessToken
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val response = try {
            client.getUserInfo("Bearer $accessToken")
        } catch (e: RestClientException) {
            throw translateUserInfoFailure(e)
        }
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
     * invalid_grant(KOE320 등)는 인가 코드가 만료됐거나 다시 쓴 것이라 클라이언트 잘못이다.
     * 그 외 4xx는 client_id·redirect_uri·scope 같은 우리 앱 설정 문제라 제공자 오류로 묶는다.
     */
    private fun translateTokenExchangeFailure(e: RestClientException): RuntimeException {
        if (e is HttpClientErrorException && parseErrorBody(e).isInvalidGrant()) {
            log.warn { "카카오 토큰 교환 거부: ${e.responseBodyAsString}" }
            return WarnException(ErrorCode.INVALID_SOCIAL_AUTH_CODE)
        }
        return providerFailure(e)
    }

    private fun translateUserInfoFailure(e: RestClientException): RuntimeException {
        if (e is HttpClientErrorException && e.statusCode == HttpStatus.UNAUTHORIZED) {
            log.warn { "카카오 사용자 조회 거부: ${e.responseBodyAsString}" }
            return WarnException(ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN)
        }
        return providerFailure(e)
    }

    // 5xx·타임아웃·앱 설정 오류. 카카오 응답 원문은 cause 에 있어 GlobalExceptionHandler 가 스택과 함께 찍는다.
    private fun providerFailure(e: RestClientException): ErrorException =
        ErrorException(ErrorCode.SOCIAL_PROVIDER_ERROR, cause = e)

    private fun parseErrorBody(e: HttpClientErrorException): KakaoTokenErrorResponse =
        runCatching { errorBodyMapper.readValue<KakaoTokenErrorResponse>(e.responseBodyAsString) }
            .getOrElse { KakaoTokenErrorResponse() }

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
        private val errorBodyMapper = ObjectMapperFactory.create()
        private const val AUTHORIZATION_URI = "https://kauth.kakao.com/oauth/authorize"
        private const val BIRTHDAY_LENGTH = 4 // MMDD

        private const val KOREA_COUNTRY_CODE = "82"
        private const val PHONE_NUMBER_LENGTH = 11 // 01012345678
        private const val KAKAO_GENDER_MALE = "male"
        private const val KAKAO_GENDER_FEMALE = "female"

        // 카카오는 콤마로 구분된 scope 목록을 허용한다.
        private const val SCOPE_DELIMITER = ","
    }
}
