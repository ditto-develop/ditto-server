package com.ditto.infrastructure.oauth.apple

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 애플 네이티브 로그인 설정.
 *
 * ID 토큰 검증만 하므로 **클라이언트 시크릿(.p8 키로 서명한 JWT)이 필요 없다.** 인가 코드 교환을 하지 않기 때문이다.
 *
 * @property clientIds ID 토큰의 `aud`로 허용할 값. 네이티브는 **앱 번들 ID**다. 웹(Services ID)이나 다른 앱을
 *   추가하면 여기에 더한다 — 하나라도 일치하면 통과한다.
 * @property jwksCacheTtl 애플 공개키 캐시 시간. 애플은 키를 주기적으로 교체하므로 영구 캐시는 안 된다.
 */
@ConfigurationProperties(prefix = "ditto.oauth.apple")
data class AppleOAuthProperties(
    val clientIds: List<String> = emptyList(),
    val jwksCacheTtl: Duration = Duration.ofHours(6),
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(5),
) {
    companion object {
        /** 애플이 발급한 ID 토큰의 발급자. 고정값이라 설정으로 빼지 않는다. */
        const val ISSUER = "https://appleid.apple.com"
    }
}
