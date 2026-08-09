package com.ditto.api.rematch

import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.rematch.service.RematchChatRoomOpener
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

// 성사 주(2026-03-09 월 ~ 03-15 일)의 주말 창은 금 00:00 ~ 월 00:00 이다.
private val MATCH_WEEK = LocalDate.of(2026, 3, 9)
private val MATCHED_ON_MONDAY = LocalDateTime.of(2026, 3, 9, 10, 0)

/** 성사 주말이 열리기 전. 예약이 곧바로 도는 정상 경로다. */
private val BEFORE_WEEKEND = LocalDateTime.of(2026, 3, 9, 10, 1)
private const val MEMBER_A = 1L
private const val MEMBER_B = 2L

class RematchChatRoomOpenerTest(
    private val rematchChatRoomOpener: RematchChatRoomOpener,
    private val memberRepository: MemberRepository,
    private val rematchRepository: RematchRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatRoomEndService: ChatRoomEndService,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    /** 양쪽이 서로를 선택해 MATCHED 가 된 쌍. 성사 시각은 [matchedAt] 이다. */
    fun saveMatchedRematch(
        memberA: Long = MEMBER_A,
        memberB: Long = MEMBER_B,
        matchedAt: LocalDateTime = MATCHED_ON_MONDAY,
        sourceGroupMatchId: Long = 1L,
    ): Rematch {
        val rematch = RematchFixture.create(
            sourceGroupMatchId = sourceGroupMatchId,
            memberIdA = memberA,
            memberIdB = memberB,
            week = OperationWeek(MATCH_WEEK),
        )
        rematch.submitWants(memberA, wants = true, now = matchedAt)
        rematch.submitWants(memberB, wants = true, now = matchedAt)
        return rematchRepository.save(rematch)
    }

    "성사된 재매칭에 방을 예약한다" - {
        "참여자 두 명이 담긴 REMATCH 방이 생긴다" {
            val rematch = saveMatchedRematch()

            rematchChatRoomOpener.openMissing(BEFORE_WEEKEND) shouldBe 1

            val room = chatRoomRepository.findAll().single()
            room.sourceType shouldBe ChatRoomType.REMATCH
            room.sourceId shouldBe rematch.id
            chatRoomMemberRepository.findByRoomIdIn(listOf(room.id))
                .map { it.memberId }.toSet() shouldBe setOf(MEMBER_A, MEMBER_B)
        }

        // 기획은 "금요일 00:00 채팅방 오픈"만 정한다 — 성사 이후 처음 오는 금요일이다.
        "성사 이후 처음 오는 금요일에 열린다" {
            saveMatchedRematch(matchedAt = MATCHED_ON_MONDAY)

            rematchChatRoomOpener.openMissing(BEFORE_WEEKEND)

            val room = chatRoomRepository.findAll().single()
            room.opensAt shouldBe LocalDateTime.of(2026, 3, 13, 0, 0)
            room.expiresAt shouldBe LocalDateTime.of(2026, 3, 16, 0, 0)
        }

        // 진행 중인 주말에 합류시키지 않는다 — 남은 몇 시간에 급히 열지 않고 온전한 72시간을 준다.
        // 합류시키면 성사가 주말 끝에 걸릴 때 몇 분짜리 방이 되거나, 예약이 도는 순간 이미 닫힌 창이 된다.
        "주말 도중에 성사되면 다음 금요일에 열린다" {
            val duringWeekend = LocalDateTime.of(2026, 3, 14, 20, 0)
            saveMatchedRematch(matchedAt = duringWeekend)

            rematchChatRoomOpener.openMissing(now = duringWeekend.plusMinutes(1)) shouldBe 1

            chatRoomRepository.findAll().single().opensAt shouldBe LocalDateTime.of(2026, 3, 20, 0, 0)
        }

        // 성사를 확정하는 것이 평가 제출이라, 일요일 늦은 밤 제출이면 그 주말은 몇 분 뒤 닫힌다.
        // 그 창에 방을 만들면 ACTIVE 로 태어난 뒤 곧바로 만료되고, 방이 존재하는 탓에 예약 조회가
        // 완료로 판정해 조용히 유실된다. 다음 금요일로 가면 그 상황이 계산되지 않는다.
        "주말 끝에 성사돼도 닫힌 창에 방을 만들지 않는다" {
            saveMatchedRematch(matchedAt = LocalDateTime.of(2026, 3, 15, 23, 59, 40))

            rematchChatRoomOpener.openMissing(now = LocalDateTime.of(2026, 3, 16, 0, 0)) shouldBe 1

            val room = chatRoomRepository.findAll().single()
            room.opensAt shouldBe LocalDateTime.of(2026, 3, 20, 0, 0)
            room.expiresAt shouldBe LocalDateTime.of(2026, 3, 23, 0, 0)
        }

        // 예약이 밀려 성사 다음 금요일마저 지났다면, 열 수 있는 가장 이른 금요일로 간다.
        "예약이 밀려 그 금요일이 지났으면 다음 금요일에 열린다" {
            saveMatchedRematch(matchedAt = MATCHED_ON_MONDAY)

            rematchChatRoomOpener.openMissing(now = LocalDateTime.of(2026, 3, 17, 9, 0)) shouldBe 1

            chatRoomRepository.findAll().single().opensAt shouldBe LocalDateTime.of(2026, 3, 20, 0, 0)
        }

        // 방이 곧 처리 완료 기록이라 별도 표시가 없다 — 조회가 다시 집어오지 않는지로 확인한다.
        "다시 불러도 방이 늘지 않는다(멱등)" {
            saveMatchedRematch()

            rematchChatRoomOpener.openMissing(BEFORE_WEEKEND) shouldBe 1
            rematchChatRoomOpener.openMissing(BEFORE_WEEKEND) shouldBe 0

            chatRoomRepository.findAll().size shouldBe 1
        }
    }

    "예약 대상이 아닌 것" - {
        "성사되지 않은 쌍에는 방을 만들지 않는다" {
            rematchRepository.save(RematchFixture.create(memberIdA = MEMBER_A, memberIdB = MEMBER_B))

            rematchChatRoomOpener.openMissing(BEFORE_WEEKEND) shouldBe 0

            chatRoomRepository.findAll().size shouldBe 0
        }

        // 한쪽이 거절하면 CANCELLED 가 된다.
        "상호 선택이 아니면 방을 만들지 않는다" {
            val rematch = RematchFixture.create(memberIdA = MEMBER_A, memberIdB = MEMBER_B)
            rematch.submitWants(MEMBER_A, wants = true, now = MATCHED_ON_MONDAY)
            rematch.submitWants(MEMBER_B, wants = false, now = MATCHED_ON_MONDAY)
            rematchRepository.save(rematch)

            rematchChatRoomOpener.openMissing(BEFORE_WEEKEND) shouldBe 0

            chatRoomRepository.findAll().size shouldBe 0
        }
    }

    "탈퇴자가 섞인 쌍" - {
        // 탈퇴는 미성사 쌍을 취소해 이 경로를 막지만, 탈퇴 가드가 읽은 뒤 상대가 제출해 성사시키는
        // 좁은 창이 남는다. 방을 만들면 남은 한쪽이 아무도 없는 방에 들어간다.
        "한쪽이 탈퇴했으면 방을 만들지 않는다" {
            val left = memberRepository.save(Member(nickname = "탈퇴한상대").apply { activate() })
            val staying = memberRepository.save(Member(nickname = "남은회원").apply { activate() })
            saveMatchedRematch(memberA = left.id, memberB = staying.id)
            left.leave(reason = "etc", now = MATCHED_ON_MONDAY)
            memberRepository.save(left)

            rematchChatRoomOpener.openMissing(BEFORE_WEEKEND) shouldBe 0

            chatRoomRepository.findAll().size shouldBe 0
        }
    }

    "종료된 방" - {
        // 종료로 예약이 되살아나면 같은 쌍의 방이 다시 열린다. 조회는 "방이 있는가"만 보므로
        // 종료 여부와 무관하게 걸러져야 한다.
        "종료된 뒤에도 다시 예약되지 않는다" {
            saveMatchedRematch()
            rematchChatRoomOpener.openMissing(BEFORE_WEEKEND)
            chatRoomEndService.endExpired(LocalDateTime.of(2026, 3, 16, 0, 0))
            chatRoomRepository.findAll().single().status shouldBe ChatRoomStatus.ENDED

            rematchChatRoomOpener.openMissing(BEFORE_WEEKEND) shouldBe 0

            chatRoomRepository.findAll().size shouldBe 1
        }
    }
})
