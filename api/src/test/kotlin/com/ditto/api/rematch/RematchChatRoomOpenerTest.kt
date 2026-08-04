package com.ditto.api.rematch

import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.rematch.service.RematchChatRoomOpener
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
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
private const val MEMBER_A = 1L
private const val MEMBER_B = 2L

class RematchChatRoomOpenerTest(
    private val rematchChatRoomOpener: RematchChatRoomOpener,
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

            rematchChatRoomOpener.openMissing() shouldBe 1

            val room = chatRoomRepository.findAll().single()
            room.sourceType shouldBe ChatRoomType.REMATCH
            room.sourceId shouldBe rematch.id
            chatRoomMemberRepository.findByRoomIdIn(listOf(room.id))
                .map { it.memberId }.toSet() shouldBe setOf(MEMBER_A, MEMBER_B)
        }

        // 개방 시각은 성사가 속한 주의 금요일이다 — 월요일에 성사되면 그 주 금요일까지 기다린다.
        "성사 주의 금요일에 열리도록 예약된다" {
            saveMatchedRematch(matchedAt = MATCHED_ON_MONDAY)

            rematchChatRoomOpener.openMissing()

            val room = chatRoomRepository.findAll().single()
            room.opensAt shouldBe LocalDateTime.of(2026, 3, 13, 0, 0)
            room.expiresAt shouldBe LocalDateTime.of(2026, 3, 16, 0, 0)
        }

        // 성사가 이미 주말 도중이면 다음 주로 미루지 않고 진행 중인 주말에 합류한다(⑧-1).
        // 개방 시각이 지난 뒤 만들어지므로 ChatRoom.of 가 곧바로 ACTIVE 로 만든다 — openDue 를 기다리지 않는다.
        "주말 도중에 성사되면 곧바로 열린 방이 된다" {
            saveMatchedRematch(matchedAt = LocalDateTime.of(2026, 3, 14, 20, 0))

            rematchChatRoomOpener.openMissing() shouldBe 1

            val room = chatRoomRepository.findAll().single()
            room.opensAt shouldBe LocalDateTime.of(2026, 3, 13, 0, 0)
            room.status shouldBe ChatRoomStatus.ACTIVE
        }

        // 방이 곧 처리 완료 기록이라 별도 표시가 없다 — 조회가 다시 집어오지 않는지로 확인한다.
        "다시 불러도 방이 늘지 않는다(멱등)" {
            saveMatchedRematch()

            rematchChatRoomOpener.openMissing() shouldBe 1
            rematchChatRoomOpener.openMissing() shouldBe 0

            chatRoomRepository.findAll().size shouldBe 1
        }
    }

    "예약 대상이 아닌 것" - {
        "성사되지 않은 쌍에는 방을 만들지 않는다" {
            rematchRepository.save(RematchFixture.create(memberIdA = MEMBER_A, memberIdB = MEMBER_B))

            rematchChatRoomOpener.openMissing() shouldBe 0

            chatRoomRepository.findAll().size shouldBe 0
        }

        // 한쪽이 거절하면 CANCELLED 가 된다.
        "상호 선택이 아니면 방을 만들지 않는다" {
            val rematch = RematchFixture.create(memberIdA = MEMBER_A, memberIdB = MEMBER_B)
            rematch.submitWants(MEMBER_A, wants = true, now = MATCHED_ON_MONDAY)
            rematch.submitWants(MEMBER_B, wants = false, now = MATCHED_ON_MONDAY)
            rematchRepository.save(rematch)

            rematchChatRoomOpener.openMissing() shouldBe 0

            chatRoomRepository.findAll().size shouldBe 0
        }
    }

    "종료된 방" - {
        // 종료로 예약이 되살아나면 같은 쌍의 방이 다시 열린다. 조회는 "방이 있는가"만 보므로
        // 종료 여부와 무관하게 걸러져야 한다.
        "종료된 뒤에도 다시 예약되지 않는다" {
            saveMatchedRematch()
            rematchChatRoomOpener.openMissing()
            chatRoomEndService.endExpired(LocalDateTime.of(2026, 3, 16, 0, 0))
            chatRoomRepository.findAll().single().status shouldBe ChatRoomStatus.ENDED

            rematchChatRoomOpener.openMissing() shouldBe 0

            chatRoomRepository.findAll().size shouldBe 1
        }
    }
})
