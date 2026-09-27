package com.ditto.api.user

import com.ditto.api.support.IntegrationTest
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.dto.UpdateMyProfileRequest
import com.ditto.api.user.facade.NicknameReservationFacade
import com.ditto.api.user.service.MyProfileService
import com.ditto.api.user.service.UserService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.NicknameReservation
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.NicknameReservationRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

/** 닉네임 10분 예약(가입 중 선점)과 프로필 수정 제한(2회/14일, 진행 중 채팅방). */
class NicknameRulesTest(
    private val nicknameReservationFacade: NicknameReservationFacade,
    private val userService: UserService,
    private val myProfileService: MyProfileService,
    private val memberRepository: MemberRepository,
    private val nicknameReservationRepository: NicknameReservationRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun savePending(nickname: String) = memberRepository.save(Member(nickname = nickname))

    fun saveActive(nickname: String) = memberRepository.save(Member(nickname = nickname).apply { activate() })

    fun registerRequest(nickname: String) = CreateUserRequest(
        nickname = nickname,
        gender = Gender.MALE,
        age = 27,
        interests = setOf("travel"),
        location = "seoul",
        job = "it-tech",
        caricature = "m1",
    )

    "닉네임 예약 (v2 확인)" - {
        "사용 가능하면 10분 동안 내게 예약한다" {
            val a = savePending("임시A")

            val result = nicknameReservationFacade.checkAndReserve(a.id, "산책러버")

            result.available shouldBe true
            result.reservedUntil.shouldNotBeNull()
            nicknameReservationRepository.findByMemberId(a.id)?.nickname shouldBe "산책러버"
        }

        "남이 예약 중이면 사용 불가다 — v1 조회도 같다" {
            val a = savePending("임시A")
            val b = savePending("임시B")
            nicknameReservationFacade.checkAndReserve(a.id, "산책러버")

            val result = nicknameReservationFacade.checkAndReserve(b.id, "산책러버")

            result.available shouldBe false
            result.reservedUntil.shouldBeNull()
            userService.checkNicknameAvailability("산책러버").available shouldBe false
        }

        "내가 다시 확인하면 예약이 연장되고, 다른 닉네임을 확인하면 예약이 옮겨 간다" {
            val a = savePending("임시A")
            nicknameReservationFacade.checkAndReserve(a.id, "산책러버")

            nicknameReservationFacade.checkAndReserve(a.id, "산책러버").available shouldBe true
            nicknameReservationFacade.checkAndReserve(a.id, "등산러버").available shouldBe true

            nicknameReservationRepository.findByNickname("산책러버").shouldBeNull()
            nicknameReservationRepository.findByMemberId(a.id)?.nickname shouldBe "등산러버"
        }

        "만료된 남의 예약은 가져올 수 있다" {
            val a = savePending("임시A")
            val b = savePending("임시B")
            nicknameReservationRepository.save(
                NicknameReservation.reserve(a.id, "산책러버", LocalDateTime.now().minusMinutes(11)),
            )

            nicknameReservationFacade.checkAndReserve(b.id, "산책러버").available shouldBe true
            nicknameReservationRepository.findByNickname("산책러버")?.memberId shouldBe b.id
        }

        "이미 저장된 닉네임은 예약할 수 없다" {
            saveActive("산책러버")
            val a = savePending("임시A")

            nicknameReservationFacade.checkAndReserve(a.id, "산책러버").available shouldBe false
        }

        "형식이 틀린 닉네임은 예약하지 않는다" {
            val a = savePending("임시A")

            shouldThrow<WarnException> {
                nicknameReservationFacade.checkAndReserve(a.id, "산책러버!!")
            }.errorCode shouldBe ErrorCode.BAD_REQUEST
        }
    }

    "가입과 예약" - {
        "남이 예약한 닉네임으로는 가입할 수 없다" {
            val a = savePending("임시A")
            val b = savePending("임시B")
            nicknameReservationFacade.checkAndReserve(a.id, "산책러버")

            shouldThrow<WarnException> {
                userService.register(b.id, registerRequest("산책러버"))
            }.errorCode shouldBe ErrorCode.NICKNAME_ALREADY_EXISTS
        }

        "내가 예약한 닉네임으로 가입하면 예약이 풀린다" {
            val a = savePending("임시A")
            nicknameReservationFacade.checkAndReserve(a.id, "산책러버")

            userService.register(a.id, registerRequest("산책러버")).nickname shouldBe "산책러버"

            nicknameReservationRepository.findByMemberId(a.id).shouldBeNull()
        }
    }

    "프로필 수정의 닉네임 제한" - {
        "두 번 바꾸면 잠기고 내 정보에 남은 횟수·해제 시각이 보인다" {
            val member = saveActive("처음")

            myProfileService.updateMyProfile(member.id, UpdateMyProfileRequest(nickname = "두번째"))
            userService.getMe(member.id).nicknameChangeRemaining shouldBe 1
            myProfileService.updateMyProfile(member.id, UpdateMyProfileRequest(nickname = "세번째"))

            val me = userService.getMe(member.id)
            me.nicknameChangeRemaining shouldBe 0
            me.nicknameChangeLockedUntil.shouldNotBeNull()
            shouldThrow<WarnException> {
                myProfileService.updateMyProfile(member.id, UpdateMyProfileRequest(nickname = "네번째"))
            }.errorCode shouldBe ErrorCode.NICKNAME_CHANGE_LOCKED
        }

        "끝나지 않은 채팅방이 있으면 바꿀 수 없다" {
            val member = saveActive("처음")
            val room = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 1L))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = member.id))

            shouldThrow<WarnException> {
                myProfileService.updateMyProfile(member.id, UpdateMyProfileRequest(nickname = "두번째"))
            }.errorCode shouldBe ErrorCode.NICKNAME_CHANGE_IN_ACTIVE_CHAT
        }

        "채팅방이 있어도 닉네임을 그대로 보내면 다른 항목은 저장된다" {
            val member = saveActive("처음")
            val room = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 1L))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = member.id))

            myProfileService.updateMyProfile(member.id, UpdateMyProfileRequest(nickname = "처음", location = "busan"))

            memberRepository.findById(member.id).get().nickname shouldBe "처음"
        }

        "남이 예약 중인 닉네임으로 바꿀 수 없다" {
            val member = saveActive("처음")
            val other = savePending("임시B")
            nicknameReservationFacade.checkAndReserve(other.id, "산책러버")

            shouldThrow<WarnException> {
                myProfileService.updateMyProfile(member.id, UpdateMyProfileRequest(nickname = "산책러버"))
            }.errorCode shouldBe ErrorCode.NICKNAME_ALREADY_EXISTS
        }
    }
})
