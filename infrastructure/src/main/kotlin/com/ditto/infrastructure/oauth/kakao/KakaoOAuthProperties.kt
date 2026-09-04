package com.ditto.infrastructure.oauth.kakao

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ditto.oauth.kakao")
data class KakaoOAuthProperties(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    // 어드민(세션 로그인)용 별도 redirect-uri. 같은 카카오 앱을 쓰되 콜백 경로만 다르다(/admin/oauth/kakao/callback).
    val adminRedirectUri: String = "",
    /**
     * 인가 요청에 실을 동의항목(scope). **앱에 설정되지 않은 동의항목을 넘기면 카카오가 로그인을 거부한다.**
     *
     * 기본값이 `profile_nickname` 하나인 이유: 이름·성별·연령대·생일·출생연도·전화번호·이메일은
     * 모두 비즈 앱(사업자 정보 등록)이라야 신청할 수 있다. 일반 앱에서 기본 제공되는 건 닉네임·프로필 사진뿐이다.
     * 비즈 앱으로 전환해 동의항목을 열면 코드가 아니라 이 설정값만 늘리면 된다.
     * 비우면 scope 파라미터 없이 요청해 앱에 설정된 동의항목을 그대로 따른다.
     */
    val scopes: List<String> = listOf("profile_nickname"),
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(5),
)
