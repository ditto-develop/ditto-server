package com.ditto.infrastructure.oauth.kakao

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.serialization.ObjectMapperFactory
import com.ditto.domain.socialaccount.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Component
class KakaoOAuthClient(
    private val properties: KakaoOAuthProperties,
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
        val params = mutableMapOf(
            OAuthClient.PARAM_GRANT_TYPE to OAuthClient.GRANT_TYPE_AUTHORIZATION_CODE,
            OAuthClient.PARAM_CLIENT_ID to properties.clientId,
            OAuthClient.PARAM_REDIRECT_URI to properties.redirectUri,
            OAuthClient.PARAM_CODE to code,
        )
        if (properties.clientSecret.isNotBlank()) {
            params[OAuthClient.PARAM_CLIENT_SECRET] = properties.clientSecret
        }
        val formData = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, Charsets.UTF_8)}=${URLEncoder.encode(v, Charsets.UTF_8)}"
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URI))
            .header("Content-Type", OAuthClient.CONTENT_TYPE_FORM_URLENCODED)
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .build()

        val httpResponse = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            logger.error(e) { "카카오 토큰 발급 요청 실패" }
            throw ErrorException(ErrorCode.OAUTH_TOKEN_FAILED, cause = e)
        }

        val body = httpResponse.body()
        if (httpResponse.statusCode() != 200) {
            logger.error { "카카오 토큰 발급 실패: status=${httpResponse.statusCode()}, body=$body" }
            throw ErrorException(ErrorCode.OAUTH_TOKEN_FAILED)
        }

        val response = try {
            objectMapper.readValue(body, KakaoTokenResponse::class.java)
        } catch (e: Exception) {
            logger.error(e) { "카카오 토큰 응답 파싱 실패: body=$body" }
            throw ErrorException(ErrorCode.OAUTH_TOKEN_FAILED, cause = e)
        }

        return response.accessToken
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(USER_INFO_URI))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()

        val httpResponse = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            logger.error(e) { "카카오 사용자 정보 요청 실패" }
            throw ErrorException(ErrorCode.OAUTH_USER_INFO_FAILED, cause = e)
        }

        val body = httpResponse.body()
        if (httpResponse.statusCode() != 200) {
            logger.error { "카카오 사용자 정보 조회 실패: status=${httpResponse.statusCode()}, body=$body" }
            throw ErrorException(ErrorCode.OAUTH_USER_INFO_FAILED)
        }

        val response = try {
            objectMapper.readValue(body, KakaoUserResponse::class.java)
        } catch (e: Exception) {
            logger.error(e) { "카카오 사용자 정보 응답 파싱 실패: body=$body" }
            throw ErrorException(ErrorCode.OAUTH_USER_INFO_FAILED, cause = e)
        }

        return OAuthUserInfo(
            id = response.id.toString(),
            nickname = response.kakaoAccount?.profile?.nickname ?: OAuthClient.DEFAULT_NICKNAME,
        )
    }
}
