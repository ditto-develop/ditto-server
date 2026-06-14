package com.ditto.api.admin.auth

import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClient
import org.springframework.stereotype.Service

/** 어드민 로그인 거부(미등록 회원 또는 비관리자). */
class AdminLoginDeniedException(message: String) : RuntimeException(message)

/**
 * 카카오 로그인으로 받은 사용자 정보를 기존 회원과 매칭하고, role=ADMIN 인 경우에만 어드민 식별 정보를 돌려준다.
 * 메인 서비스와 달리 회원을 생성하지 않으며, 어드민 전용 redirect-uri 클라이언트([adminKakaoClient])를 사용한다.
 */
@Service
class AdminLoginService(
    private val adminKakaoClient: OAuthClient,
    private val memberSocialAccountService: MemberSocialAccountService,
) {
    fun authorizationUrl(): String = adminKakaoClient.getAuthorizationUrl()

    fun login(code: String): AdminPrincipal {
        val accessToken = adminKakaoClient.getAccessToken(code)
        val userInfo = adminKakaoClient.getUserInfo(accessToken)

        val member = memberSocialAccountService.findMemberBySocial(SocialProvider.KAKAO, userInfo.id)
            ?: throw AdminLoginDeniedException("등록되지 않은 회원입니다.")

        if (!member.isAdmin()) {
            throw AdminLoginDeniedException("관리자 권한이 없습니다.")
        }

        return AdminPrincipal(memberId = member.id, name = member.name, email = member.email)
    }
}
