package com.ditto.infrastructure.oauth.apple

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 애플 로그인 설정.
 *
 * 로그인 인증은 ID 토큰 검증으로 끝난다. 클라이언트 시크릿(.p8 키)은 탈퇴 때 애플 토큰을 폐기하려고
 * 인가 코드를 교환할 때만 쓴다.
 *
 * @property clientIds ID 토큰의 `aud`로 허용할 값. 네이티브는 **앱 번들 ID**다. 웹(Services ID)이나 다른 앱을
 *   추가하면 여기에 더한다 — 하나라도 일치하면 통과한다.
 * @property webClientId 웹 로그인의 `client_id` — 애플 개발자 콘솔의 **Services ID**다(앱 번들 ID가 아니다).
 *   [clientIds]에도 함께 넣어야 웹에서 받은 ID 토큰의 `aud` 검증을 통과한다.
 * @property webRedirectUri 애플이 폼 POST 로 콜백할 우리 서버 주소. 콘솔의 Return URL 과 정확히 같아야 한다.
 * @property adminWebRedirectUri 어드민 로그인의 Return URL. 유저 웹과 [webClientId] 는 공유하고 돌아올 주소만 다르다
 *   — 콘솔의 한 Services ID 에 Return URL 을 둘 다 등록해야 한다.
 * @property jwksCacheTtl 애플 공개키 캐시 시간. 애플은 키를 주기적으로 교체하므로 영구 캐시는 안 된다.
 * @property teamId 애플 개발자 팀 ID. 아래 둘과 함께 client_secret 서명에 쓴다.
 * @property keyId Sign in with Apple 키(.p8)의 키 ID.
 * @property privateKey .p8 파일 내용(PEM). 셋 중 하나라도 비면 인가 코드 교환과 토큰 폐기를 건너뛴다.
 */
@ConfigurationProperties(prefix = "ditto.oauth.apple")
data class AppleOAuthProperties(
    val clientIds: List<String> = emptyList(),
    val webClientId: String = "",
    val webRedirectUri: String = "",
    val adminWebRedirectUri: String = "",
    val jwksCacheTtl: Duration = Duration.ofHours(6),
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(5),
    val teamId: String = "",
    val keyId: String = "",
    val privateKey: String = "",
) {
    fun canSignClientSecret(): Boolean = teamId.isNotBlank() && keyId.isNotBlank() && privateKey.isNotBlank()

    private val maskedPrivateKey: String
        get() = if (privateKey.isBlank()) "" else "****"

    // 로그에 비밀키가 찍히지 않게 data class 의 toString 을 덮는다.
    override fun toString(): String =
        "AppleOAuthProperties(clientIds=$clientIds, webClientId=$webClientId, teamId=$teamId, keyId=$keyId, " +
            "privateKey=$maskedPrivateKey)"

    companion object {
        /** 애플이 발급한 ID 토큰의 발급자. 고정값이라 설정으로 빼지 않는다. */
        const val ISSUER = "https://appleid.apple.com"
    }
}
