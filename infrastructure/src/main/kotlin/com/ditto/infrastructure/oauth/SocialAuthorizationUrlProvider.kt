package com.ditto.infrastructure.oauth

/**
 * 소셜 로그인 인가 페이지 URL을 만든다.
 *
 * [OAuthClient]에서 이 능력만 떼어낸 이유: 애플 웹 로그인은 인가 URL은 필요하지만
 * 코드 교환도 userinfo 조회도 하지 않는다(콜백이 `id_token`을 함께 주므로 그것을 검증한다).
 * 한 인터페이스에 묶으면 애플 구현의 두 메서드가 "지원하지 않음"이 된다.
 */
interface SocialAuthorizationUrlProvider {
    fun getAuthorizationUrl(): String
}
