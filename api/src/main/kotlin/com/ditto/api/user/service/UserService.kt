package com.ditto.api.user.service

import com.ditto.api.match.MatchAccessChecker
import com.ditto.api.system.ServerTimeProvider
import com.ditto.api.user.dto.CheckNicknameResponse
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.dto.LeaveRequest
import com.ditto.api.user.dto.LeaveResponse
import com.ditto.api.user.dto.MeResponse
import com.ditto.api.user.dto.PublicProfileResponse
import com.ditto.api.user.dto.RegisterResponse
import com.ditto.api.user.dto.toLeaveResponse
import com.ditto.api.user.dto.toMeResponse
import com.ditto.api.user.dto.toRegisterResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val memberRepository: MemberRepository,
    private val memberBlockRepository: MemberBlockRepository,
    private val introNoteRepository: IntroNoteRepository,
    private val matchAccessChecker: MatchAccessChecker,
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val serverTimeProvider: ServerTimeProvider,
    private val leaveProgressChecker: LeaveProgressChecker,
    private val leftMemberRematchCanceller: LeftMemberRematchCanceller,
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
            interests = request.interests.map { Interest.from(it) }.toSet(),
            location = Location.from(request.location),
            job = Job.from(request.job),
            caricature = request.caricature,
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

    /**
     * 타인 공개 프로필 조회. 매칭이 성사된 상대(또는 같은 그룹채팅 참여자)만 조회 가능.
     * 민감정보(email·전화번호·실명)는 반환하지 않는다.
     *
     * 차단한/차단당한 상대는 매칭 이력이 있어도 볼 수 없다 —
     * "차단한 사용자는 나의 프로필을 볼 수 없고"(피그마 6.2.2)를 집행하는 지점이다.
     */
    @Transactional(readOnly = true)
    fun getPublicProfile(viewerId: Long, targetId: Long): PublicProfileResponse {
        if (viewerId != targetId && !matchAccessChecker.isMatched(viewerId, targetId)) {
            throw WarnException(ErrorCode.FORBIDDEN)
        }
        if (viewerId != targetId && memberBlockRepository.existsBetween(viewerId, targetId)) {
            throw WarnException(ErrorCode.FORBIDDEN)
        }

        val member = memberRepository.findById(targetId).orElseThrow {
            WarnException(ErrorCode.NOT_FOUND)
        }

        // 자기소개는 소개노트 ONE_WORD 답변으로 채운다. 미작성/공백이면 null.
        val introduction = introNoteRepository.findByMemberIdAndQuestion(targetId, IntroQuestion.ONE_WORD)
            ?.answer
            ?.ifBlank { null }

        return PublicProfileResponse(
            userId = member.id,
            nickname = member.nickname,
            gender = member.gender?.name,
            age = member.age,
            introduction = introduction,
            profileImageUrl = member.caricature,
            location = member.location?.code,
            occupation = member.job?.code,
            interests = member.interests.map { it.code },
        )
    }

    @Transactional(readOnly = true)
    fun checkNicknameAvailability(nickname: String): CheckNicknameResponse {
        if (memberRepository.existsByNickname(nickname)) {
            throw WarnException(ErrorCode.NICKNAME_ALREADY_EXISTS)
        }
        return CheckNicknameResponse(available = true)
    }

    /**
     * 탈퇴(소프트 삭제). 데이터를 지우지 않고 상태만 LEFT로 바꾼다 —
     * 화면이 "30일 이내 재가입하면 계정을 복구할 수 있습니다"를 안내하므로 즉시 삭제할 수 없다.
     * 실제 삭제는 [LeftMemberPurgeService]가 보존 기간 경과 후 수행한다.
     *
     * 제재 중 탈퇴를 더는 막지 않는다 — hard delete 시절에는 제재 이력과 재가입 식별 근거가
     * 함께 사라져 차단 우회 수단이 됐지만, 소프트 삭제는 둘을 모두 보존한다.
     */
    @Transactional
    fun leaveUser(id: Long, memberId: Long, request: LeaveRequest): LeaveResponse {
        val member = memberRepository.findById(id).orElseThrow {
            WarnException(ErrorCode.NOT_FOUND)
        }

        if (memberId != id) {
            throw WarnException(ErrorCode.FORBIDDEN)
        }

        // "진행 중인 매칭이나 채팅이 있으면 탈퇴가 제한됩니다" — 상대가 기다리는 상태를 남기지 않는다.
        if (leaveProgressChecker.hasInProgress(id)) {
            throw WarnException(ErrorCode.CANNOT_LEAVE_WHILE_IN_PROGRESS)
        }

        member.leave(reason = request.reason, now = serverTimeProvider.now())

        leftMemberRematchCanceller.cancelWaitingPairs(id)

        // 세션은 즉시 끊는다. SocialAccount는 복구·재가입 식별 근거이므로 남긴다.
        refreshTokenRepository.deleteAllByMemberId(id)

        return member.toLeaveResponse()
    }
}
