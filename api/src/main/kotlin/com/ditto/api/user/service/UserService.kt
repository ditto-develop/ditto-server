package com.ditto.api.user.service

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.user.dto.CheckNicknameResponse
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.dto.LeaveResponse
import com.ditto.api.user.dto.RegisterResponse
import com.ditto.api.user.dto.toLeaveResponse
import com.ditto.api.user.dto.toRegisterResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @Transactional
    fun register(request: CreateUserRequest): RegisterResponse {
        val socialAccount =
            socialAccountRepository.findByProviderAndProviderUserId(request.provider, request.providerUserId)
                ?: throw ErrorException(ErrorCode.NOT_FOUND)

        val member = memberRepository.findById(socialAccount.memberId).orElseThrow {
            ErrorException(ErrorCode.INTERNAL_ERROR)
        }

        if (!member.isPending()) {
            throw ErrorException(ErrorCode.MEMBER_ALREADY_EXISTS)
        }

        if (request.nickname != null && memberRepository.existsByNickname(request.nickname)) {
            throw WarnException(ErrorCode.NICKNAME_ALREADY_EXISTS)
        }

        member.register(
            name = request.name,
            nickname = request.nickname,
            phoneNumber = request.phoneNumber,
            gender = request.gender,
            age = request.age,
            birthDate = request.birthDate,
            email = request.email,
        )

        return member.toRegisterResponse()
    }

    @Transactional(readOnly = true)
    fun checkNicknameAvailability(nickname: String): CheckNicknameResponse {
        if (memberRepository.existsByNickname(nickname)) {
            throw WarnException(ErrorCode.NICKNAME_ALREADY_EXISTS)
        }
        return CheckNicknameResponse(available = true)
    }

    @Transactional
    fun leaveUser(id: Long, principal: MemberPrincipal): LeaveResponse {
        val member = memberRepository.findById(id).orElseThrow {
            WarnException(ErrorCode.NOT_FOUND)
        }

        val socialAccount =
            socialAccountRepository.findByProviderAndProviderUserId(principal.provider, principal.providerUserId)
                ?: throw ErrorException(ErrorCode.UNAUTHORIZED_ERROR)

        if (socialAccount.memberId != id) {
            throw WarnException(ErrorCode.FORBIDDEN)
        }

        val response = member.toLeaveResponse()

        refreshTokenRepository.deleteAllByMemberId(id)
        socialAccountRepository.delete(socialAccount)
        memberRepository.delete(member)

        return response
    }
}
