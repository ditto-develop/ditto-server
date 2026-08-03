package com.ditto.api.chat

import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatEndReason
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.sql.DataSource

private val FRIDAY = LocalDateTime.of(2026, 3, 13, 12, 0)
private val WEDNESDAY = LocalDateTime.of(2026, 3, 11, 9, 0)
private val AFTER_EXPIRY = LocalDateTime.of(2026, 3, 16, 0, 0)

class ChatRoomEndServiceTest(
    private val chatRoomEndService: ChatRoomEndService,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveRoomWithMembers(now: LocalDateTime = FRIDAY, vararg memberIds: Long) =
        chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 100L, now = now)).also { room ->
            chatRoomMemberRepository.saveAll(memberIds.map { ChatRoomMember.of(roomId = room.id, memberId = it) })
        }

    "만료 마감" - {
        "기한이 지난 방을 마감한다" {
            saveRoomWithMembers(FRIDAY, 1L, 2L)

            val ended = chatRoomEndService.endExpired(AFTER_EXPIRY)

            ended.size shouldBe 1
            chatRoomRepository.findAll().first().status shouldBe ChatRoomStatus.ENDED
            chatRoomRepository.findAll().first().endReason shouldBe ChatEndReason.EXPIRED
        }

        "기한 전이면 마감하지 않는다" {
            saveRoomWithMembers(FRIDAY, 1L, 2L)

            chatRoomEndService.endExpired(LocalDateTime.of(2026, 3, 15, 23, 59)).size shouldBe 0
            chatRoomRepository.findAll().first().status shouldBe ChatRoomStatus.ACTIVE
        }

        "열리지 못한 채 기한이 지난 예약 방도 함께 마감한다" {
            saveRoomWithMembers(WEDNESDAY, 1L, 2L)
            chatRoomRepository.findAll().first().status shouldBe ChatRoomStatus.SCHEDULED

            chatRoomEndService.endExpired(AFTER_EXPIRY).size shouldBe 1

            chatRoomRepository.findAll().first().status shouldBe ChatRoomStatus.ENDED
        }

        "이미 마감된 방은 다시 세지 않는다(멱등)" {
            saveRoomWithMembers(FRIDAY, 1L, 2L)
            chatRoomEndService.endExpired(AFTER_EXPIRY)

            chatRoomEndService.endExpired(AFTER_EXPIRY).size shouldBe 0
        }
    }

    "예약 방 개방" - {
        "개방 시각이 지나면 연다" {
            saveRoomWithMembers(WEDNESDAY, 1L, 2L)

            val opened = chatRoomEndService.openDue(FRIDAY)

            opened.size shouldBe 1
            chatRoomRepository.findAll().first().status shouldBe ChatRoomStatus.ACTIVE
        }

        "개방 시각 전이면 열지 않는다" {
            saveRoomWithMembers(WEDNESDAY, 1L, 2L)

            chatRoomEndService.openDue(LocalDateTime.of(2026, 3, 12, 23, 59)).size shouldBe 0
            chatRoomRepository.findAll().first().status shouldBe ChatRoomStatus.SCHEDULED
        }
    }

    "사용자 종료" - {
        "방을 끝내고 누가 나갔는지 시스템 메시지로 남긴다" {
            val room = saveRoomWithMembers(FRIDAY, 1L, 2L)

            chatRoomEndService.endByUser(roomId = room.id, memberId = 1L, now = FRIDAY)

            chatRoomRepository.findAll().first().endReason shouldBe ChatEndReason.USER_ENDED

            val messages = chatMessageRepository.findAll()
            messages.size shouldBe 1
            messages.first().messageType shouldBe ChatMessageType.SYSTEM
            messages.first().content shouldBe ChatRoomEndService.USER_LEFT
            // 조회자는 이 senderId 로 "상대방이 종료했다"를 판별한다
            messages.first().senderId shouldBe 1L
        }

        "재요청해도 시스템 메시지가 하나만 남는다(멱등)" {
            val room = saveRoomWithMembers(FRIDAY, 1L, 2L)

            chatRoomEndService.endByUser(room.id, memberId = 1L, now = FRIDAY)
            chatRoomEndService.endByUser(room.id, memberId = 1L, now = FRIDAY)

            chatMessageRepository.findAll().size shouldBe 1
        }

        "이미 만료된 방을 사용자가 끝내도 만료 기록이 유지된다" {
            val room = saveRoomWithMembers(FRIDAY, 1L, 2L)
            chatRoomEndService.endExpired(AFTER_EXPIRY)

            chatRoomEndService.endByUser(room.id, memberId = 1L, now = AFTER_EXPIRY)

            chatRoomRepository.findAll().first().endReason shouldBe ChatEndReason.EXPIRED
            chatMessageRepository.findAll().size shouldBe 0
        }

        "방 멤버가 아니면 거부한다" {
            val room = saveRoomWithMembers(FRIDAY, 1L, 2L)

            shouldThrow<WarnException> {
                chatRoomEndService.endByUser(room.id, memberId = 99L, now = FRIDAY)
            }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
        }

        "없는 방이면 거부한다" {
            shouldThrow<WarnException> {
                chatRoomEndService.endByUser(roomId = 9999L, memberId = 1L, now = FRIDAY)
            }.errorCode shouldBe ErrorCode.CHAT_ROOM_NOT_FOUND
        }
    }

    // 방 행 잠금이 겹친 종료를 직렬화하는지 확인한다. isEnded 판정만으로는 부족한 지점 —
    // 겹친 요청은 서로의 커밋 전 상태를 보므로 둘 다 "아직 안 끝났다"로 판단해 종료 기록이 덮인다.
    // 더블 탭·클라이언트 재시도가 실제로 겹치는 경로다.
    // 이 테스트의 DB 는 H2 라 잠금 계약 위반은 잡지만 InnoDB 시맨틱까지 보장하지는 않는다.
    "동시 종료" - {
        "양쪽이 동시에 종료해도 종료 기록이 하나로 수렴한다" {
            val room = saveRoomWithMembers(FRIDAY, 1L, 2L)
            val startLatch = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)

            val requests = listOf(1L, 2L).map { memberId ->
                executor.submit {
                    startLatch.await()
                    chatRoomEndService.endByUser(room.id, memberId, FRIDAY)
                }
            }
            startLatch.countDown()
            requests.forEach { it.get() }
            executor.shutdown()

            chatRoomRepository.findAll().first().isEnded shouldBe true
            // 먼저 잠근 쪽만 실제로 끝내므로 종료 메시지도 하나뿐이다
            chatMessageRepository.findAll().size shouldBe 1
        }
    }
})
