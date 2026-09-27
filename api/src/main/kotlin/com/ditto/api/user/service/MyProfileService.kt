package com.ditto.api.user.service

import com.ditto.api.intronote.service.IntroNoteService
import com.ditto.api.system.ServerTimeProvider
import com.ditto.api.user.dto.MyRatingsResponse
import com.ditto.api.user.dto.MyStatsResponse
import com.ditto.api.user.dto.PublicProfileResponse
import com.ditto.api.user.dto.UpdateMyProfileRequest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.ProfileChanges
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.repository.ReviewAnswerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 마이프로필 화면(`6.1 프로필`, `6.1.1 프로필 수정`) 전용 서비스.
 *
 * 조회는 타인 공개 프로필과 같은 응답을 쓴다 — [UserService.getPublicProfile]이 이미
 * 본인 조회(`viewerId == targetId`)를 허용하므로 별칭으로 재사용한다.
 */
@Service
class MyProfileService(
    private val userService: UserService,
    private val introNoteService: IntroNoteService,
    private val memberRepository: MemberRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val memberRatingService: MemberRatingService,
    private val nicknameService: NicknameService,
    private val chatRoomRepository: ChatRoomRepository,
    private val serverTimeProvider: ServerTimeProvider,
) {

    @Transactional(readOnly = true)
    fun getMyProfile(memberId: Long): PublicProfileResponse =
        userService.getPublicProfile(memberId, memberId)

    /**
     * 프로필 수정 화면의 항목을 갱신한다. null인 항목은 건드리지 않는다.
     *
     * 닉네임은 2회 바꾸면 14일 잠기고([com.ditto.domain.member.entity.Member.changeNickname]), 끝나지 않은 채팅방이
     * 있으면 바꿀 수 없다. 지금과 같은 닉네임을 보내면 변경이 아니라 그냥 통과한다.
     *
     * 한 줄 소개는 `member`가 아니라 소개노트 `ONE_WORD`에 저장된다 — 프로필 조회가 그 답변을
     * 읽어 `introduction`으로 내려주므로, 저장 위치를 한 곳으로 유지해야 두 화면이 어긋나지 않는다.
     */
    @Transactional
    fun updateMyProfile(memberId: Long, request: UpdateMyProfileRequest): PublicProfileResponse {
        val member = memberRepository.findById(memberId).orElseThrow {
            WarnException(ErrorCode.NOT_FOUND)
        }

        request.nickname?.takeIf { it != member.nickname }?.let { nickname ->
            // 대화 상대가 보던 이름이 대화 도중 바뀌면 누구와 이야기하는지 헷갈린다. 예약·진행 중인 방(1:1·그룹·재매칭)이
            // 하나라도 있으면 막고, 내가 나간 방은 세지 않는다.
            if (chatRoomRepository.existsUnendedRoomOfMember(memberId)) {
                throw WarnException(ErrorCode.NICKNAME_CHANGE_IN_ACTIVE_CHAT)
            }
            nicknameService.assertAssignable(memberId, nickname)
            member.changeNickname(nickname, serverTimeProvider.now())
            nicknameService.release(memberId)
        }

        member.updateProfile(
            ProfileChanges(
                gender = request.gender,
                location = request.location?.let { Location.from(it) },
                job = request.occupation?.let { Job.from(it) },
                caricature = request.profileImageUrl,
                interests = request.interests?.map { Interest.from(it) }?.toSet(),
            ),
        )

        request.introduction?.let { introduction ->
            if (introduction.isBlank()) {
                throw WarnException(ErrorCode.BAD_REQUEST, "한 줄 소개를 입력해 주세요.")
            }
            introNoteService.saveAnswer(memberId, IntroQuestion.ONE_WORD.code, introduction.trim())
        }

        return userService.getPublicProfile(memberId, memberId)
    }

    @Transactional(readOnly = true)
    fun getMyStats(memberId: Long): MyStatsResponse = MyStatsResponse(
        participationWeeks = quizProgressRepository.countByMemberIdAndStatus(
            memberId = memberId,
            status = QuizProgressStatus.COMPLETED,
        ),
        matchCount = chatRoomMemberRepository.countByMemberId(memberId),
        meetingCount = reviewAnswerRepository.countByReviewedMemberIdAndMeetingStatusAndAnsweredAtIsNotNull(
            reviewedMemberId = memberId,
            meetingStatus = MeetingStatus.MET,
        ),
    )

    /** 집계와 공개 기준은 [MemberRatingService]가 갖는다 — 타인 프로필도 같은 값을 봐야 한다. */
    @Transactional(readOnly = true)
    fun getMyRatings(memberId: Long): MyRatingsResponse = memberRatingService.getRatings(memberId)
}
