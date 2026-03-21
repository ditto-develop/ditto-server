package com.ditto.infrastructure.oauth.kakao

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.serialization.ObjectMapperFactory
import com.ditto.domain.socialaccount.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class KakaoOAuthClient(
    private val properties: KakaoOAuthProperties,
    private val client: KakaoApiClient,
) : OAuthClient {

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val AUTHORIZATION_URI = "https://kauth.kakao.com/oauth/authorize"
        private const val TOKEN_URI = "https://kauth.kakao.com/oauth/token"
        private const val USER_INFO_URI = "https://kapi.kakao.com/v2/user/me"
    }

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapperFactory.create()

    override fun getProvider(): SocialProvider = SocialProvider.KAKAO
    override fun getAuthorizationUri(): String = AUTHORIZATION_URI
    override fun getClientId(): String = properties.clientId
    override fun getRedirectUri(): String = properties.redirectUri

    override fun getAccessToken(code: String): String {
        val formData = buildTokenRequestParams(code)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URI))
            .header("Content-Type", OAuthClient.CONTENT_TYPE_FORM_URLENCODED)
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .build()

        val body = send(request, ErrorCode.OAUTH_TOKEN_FAILED)
        return parse(body, KakaoTokenResponse::class.java, ErrorCode.OAUTH_TOKEN_FAILED).accessToken
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(USER_INFO_URI))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()

        client.requestAuth()

        val body = send(request, ErrorCode.OAUTH_USER_INFO_FAILED)
        val response = parse(body, KakaoUserResponse::class.java, ErrorCode.OAUTH_USER_INFO_FAILED)
        return OAuthUserInfo(
            id = response.id.toString(),
            nickname = response.kakaoAccount?.profile?.nickname ?: OAuthClient.DEFAULT_NICKNAME,
        )
    }

    private fun buildTokenRequestParams(code: String): String {
        val params = mutableMapOf(
            OAuthClient.PARAM_GRANT_TYPE to OAuthClient.GRANT_TYPE_AUTHORIZATION_CODE,
            OAuthClient.PARAM_CLIENT_ID to properties.clientId,
            OAuthClient.PARAM_REDIRECT_URI to properties.redirectUri,
            OAuthClient.PARAM_CODE to code,
        )
        if (properties.clientSecret.isNotBlank()) {
            params[OAuthClient.PARAM_CLIENT_SECRET] = properties.clientSecret
        }
        return params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, Charsets.UTF_8)}=${URLEncoder.encode(v, Charsets.UTF_8)}"
        }
    }

    private fun send(request: HttpRequest, errorCode: ErrorCode): String {
        val httpResponse = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            logger.error(e) { "카카오 API 요청 실패: ${request.uri()}" }
            throw ErrorException(errorCode, cause = e)
        }

        val body = httpResponse.body()
        if (httpResponse.statusCode() != 200) {
            logger.error { "카카오 API 실패: uri=${request.uri()}, status=${httpResponse.statusCode()}, body=$body" }
            throw ErrorException(errorCode)
        }
        return body
    }

    private fun <T> parse(body: String, responseType: Class<T>, errorCode: ErrorCode): T =
        try {
            objectMapper.readValue(body, responseType)
        } catch (e: Exception) {
            logger.error(e) { "카카오 API 응답 파싱 실패: body=$body" }
            throw ErrorException(errorCode, cause = e)
        }
}
