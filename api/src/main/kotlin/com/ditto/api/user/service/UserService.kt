package com.ditto.api.user.service

import com.ditto.api.user.dto.CheckNicknameResponse
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.dto.LeaveResponse
import com.ditto.api.user.dto.MeResponse
import com.ditto.api.user.dto.RegisterResponse
import com.ditto.api.user.dto.toLeaveResponse
import com.ditto.api.user.dto.toMeResponse
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
    fun register(memberId: Long, request: CreateUserRequest): RegisterResponse {
        // memberId는 JWT 필터가 검증해 넘긴 값이라 회원이 존재해야 정상이다.
        // 여기서 못 찾으면 클라이언트 잘못이 아니라 토큰-DB 정합성이 깨진 서버 오류다.
        val member = memberRepository.findById(memberId).orElseThrow {
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
    fun getMe(memberId: Long): MeResponse {
        val member = memberRepository.findById(memberId).orElseThrow {
            WarnException(ErrorCode.NOT_FOUND)
        }
        return member.toMeResponse()
    }

    @Transactional(readOnly = true)
    fun checkNicknameAvailability(nickname: String): CheckNicknameResponse {
        if (memberRepository.existsByNickname(nickname)) {
            throw WarnException(ErrorCode.NICKNAME_ALREADY_EXISTS)
        }
        return CheckNicknameResponse(available = true)
    }

    @Transactional
    fun leaveUser(id: Long, memberId: Long): LeaveResponse {
        val member = memberRepository.findById(id).orElseThrow {
            WarnException(ErrorCode.NOT_FOUND)
        }

        if (memberId != id) {
            throw WarnException(ErrorCode.FORBIDDEN)
        }

        val response = member.toLeaveResponse()

        refreshTokenRepository.deleteAllByMemberId(id)
        socialAccountRepository.findByMemberId(id)?.let { socialAccountRepository.delete(it) }
        memberRepository.delete(member)

        return response
    }
}
