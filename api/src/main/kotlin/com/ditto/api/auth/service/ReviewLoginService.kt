package com.ditto.api.auth.service

import com.ditto.api.auth.dto.ReviewLoginRequest
import com.ditto.api.config.ReviewLoginProperties
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.socialaccount.entity.SocialProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest

/**
 * App Store 심사용 데모 로그인의 인증.
 *
 * 리뷰어는 카카오·애플 인증을 대신 통과할 수 없어서, ID·비밀번호가 맞으면 정해 둔 소셜 계정
 * ([PROVIDER]·[PROVIDER_USER_ID]) 하나로 로그인한 것처럼 처리한다. 소셜 계정으로 다루는 이유는
 * 회원 생성·탈퇴 복구·제재 판정·토큰 발급이 소셜 로그인과 같은 경로를 타게 하기 위해서다.
 *
 * 꺼져 있을 때·ID 불일치·비밀번호 불일치는 모두 같은 [ErrorCode.INVALID_REVIEW_LOGIN] 이다.
 * 어느 쪽이 틀렸는지, 기능이 켜져 있는지를 응답으로 알려 주지 않는다.
 */
@Service
class ReviewLoginService(
    private val properties: ReviewLoginProperties,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    fun authenticate(request: ReviewLoginRequest) {
        if (!properties.enabled) throw WarnException(ErrorCode.INVALID_REVIEW_LOGIN)

        // 비밀번호는 ID가 틀려도 대조한다. ID에서 바로 끊으면 응답 시간으로 ID 일치 여부가 드러난다.
        val idMatches = MessageDigest.isEqual(request.loginId.toByteArray(), properties.loginId.toByteArray())
        val passwordMatches = passwordEncoder.matches(request.password, properties.passwordHash)
        if (!idMatches || !passwordMatches) {
            log.warn { "심사용 로그인 실패 (idMatches=$idMatches)" }
            throw WarnException(ErrorCode.INVALID_REVIEW_LOGIN)
        }
    }

    companion object {
        private val log = KotlinLogging.logger {}

        /** 리뷰어는 iOS 앱으로 들어오므로 애플 회원으로 둔다. */
        val PROVIDER = SocialProvider.APPLE

        /**
         * 실제 애플 `sub`(`000123.abc….0123` 형식)와 겹치지 않는 고정값.
         * 처음 로그인하면 이 소셜 계정으로 회원이 생기고, 이후에는 같은 회원으로 들어간다.
         */
        const val PROVIDER_USER_ID = "app-store-review"
    }
}
