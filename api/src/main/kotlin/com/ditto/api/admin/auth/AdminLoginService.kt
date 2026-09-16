package com.ditto.api.admin.auth

import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.NativeSocialAuthenticatorFactory
import com.ditto.infrastructure.oauth.NativeSocialCredential
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.SocialAuthorizationUrlProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/** 어드민 로그인 거부(미등록 회원 또는 비관리자). */
class AdminLoginDeniedException(message: String) : RuntimeException(message)

/**
 * 소셜 로그인으로 받은 사용자 정보를 기존 회원과 매칭하고, role=ADMIN 인 경우에만 어드민 식별 정보를 돌려준다.
 * 메인 서비스와 달리 **회원을 생성하지 않으며**, 어드민 전용 redirect-uri 클라이언트를 사용한다.
 *
 * 제공자별로 "소셜 식별자를 얻는 방법"만 다르고([login] 카카오는 코드 교환, [loginWithAppleIdToken] 애플은
 * ID 토큰 검증), 그 뒤 회원 매칭·ADMIN 판정은 [authorizeAdmin] 한 곳으로 모은다.
 */
@Service
class AdminLoginService(
    private val adminKakaoClient: OAuthClient,
    @Qualifier("adminAppleAuthorizationUrlProvider")
    private val adminAppleAuthorizationUrlProvider: SocialAuthorizationUrlProvider,
    private val nativeSocialAuthenticatorFactory: NativeSocialAuthenticatorFactory,
    private val memberSocialAccountService: MemberSocialAccountService,
) {
    fun authorizationUrl(): String = adminKakaoClient.getAuthorizationUrl()

    fun appleAuthorizationUrl(): String = adminAppleAuthorizationUrlProvider.getAuthorizationUrl()

    fun login(code: String): AdminPrincipal {
        val accessToken = adminKakaoClient.getAccessToken(code)
        val userInfo = adminKakaoClient.getUserInfo(accessToken)

        return authorizeAdmin(SocialProvider.KAKAO, userInfo.id)
    }

    /**
     * 애플 웹 콜백으로 함께 온 ID 토큰을 검증해 로그인한다 — 인가 코드는 쓰지 않는다(ADR 0023).
     * 검증기는 앱/웹 로그인과 같은 것을 그대로 쓴다.
     *
     * 토큰 검증 실패(`WarnException`)를 [AdminLoginDeniedException] 으로 바꿔 던지는 이유: 어드민은 JSON API 가
     * 아니라 **서버 렌더 화면**이다. 그대로 두면 `GlobalExceptionHandler` 가 JSON 에러 바디를 뱉어 관리자가 로그인
     * 페이지 대신 날 JSON 을 보게 된다. 콘솔에 Return URL·Services ID 가 아직 등록되지 않은 구간에서 바로 이 경로를 탄다.
     */
    fun loginWithAppleIdToken(idToken: String): AdminPrincipal {
        val userInfo = try {
            nativeSocialAuthenticatorFactory
                .getAuthenticator(SocialProvider.APPLE)
                .authenticate(NativeSocialCredential(token = idToken))
        } catch (e: WarnException) {
            throw AdminLoginDeniedException("애플 인증에 실패했습니다: ${e.errorCode.code}")
        }

        return authorizeAdmin(SocialProvider.APPLE, userInfo.id)
    }

    private fun authorizeAdmin(provider: SocialProvider, providerUserId: String): AdminPrincipal {
        val member: Member = memberSocialAccountService.findMemberBySocial(provider, providerUserId)
            ?: throw AdminLoginDeniedException("등록되지 않은 회원입니다.")

        if (!member.isAdmin()) {
            throw AdminLoginDeniedException("관리자 권한이 없습니다.")
        }

        return AdminPrincipal(memberId = member.id, name = member.name, email = member.email)
    }
}
