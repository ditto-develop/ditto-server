package com.ditto.api.chat

import com.ditto.api.chat.dto.ChatImageUploadFileRequest
import com.ditto.api.chat.dto.ChatImageUploadUrlsRequest
import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.chat.service.ChatService
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
    private val chatService: ChatService,
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

        // 개방 후보 조회가 종료된 방을 걸러내는지 확인한다. 되살아나면 SCHEDULED→ACTIVE→ENDED 단방향
        // 불변식이 깨지고 나간 사람의 상대가 계속 메시지를 보낼 수 있게 된다.
        //
        // 다만 이 테스트가 덮는 것은 "조회 필터"까지다. 후보 조회와 잠금 사이의 경합 구간(그 사이에
        // 사용자가 끝내는 경우)은 순차 실행으로 창을 만들 수 없어 여기서 재현되지 않는다 —
        // 그 구간은 findWithLockById 이후의 상태 재확인이 막는다.
        "종료된 방은 개방 후보에서 빠진다" {
            val room = saveRoomWithMembers(WEDNESDAY, 1L, 2L)
            chatRoomEndService.endByUser(room.id, memberId = 1L, now = WEDNESDAY)

            chatRoomEndService.openDue(FRIDAY).size shouldBe 0

            val reloaded = chatRoomRepository.findAll().first()
            reloaded.status shouldBe ChatRoomStatus.ENDED
            reloaded.endReason shouldBe ChatEndReason.USER_ENDED
            reloaded.endedAt shouldBe WEDNESDAY
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

        // 재매칭 방도 두 사람만 있는 1:1 이라 나가기가 되어야 한다. 검사를 "1:1인가"가 아니라
        // "그룹인가"로 두는 이유가 이것이다 — 두 사람 방이 늘 때 자동으로 허용된다.
        "재매칭 방도 사용자가 끝낼 수 있다" {
            val room = chatRoomRepository.save(ChatRoomFixture.rematch(sourceId = 200L, now = FRIDAY))
            chatRoomMemberRepository.saveAll(
                listOf(
                    ChatRoomMember.of(roomId = room.id, memberId = 1L),
                    ChatRoomMember.of(roomId = room.id, memberId = 2L),
                ),
            )

            chatRoomEndService.endByUser(room.id, memberId = 1L, now = FRIDAY)

            chatRoomRepository.findAll().first().endReason shouldBe ChatEndReason.USER_ENDED
            chatMessageRepository.findAll().first().content shouldBe ChatRoomEndService.USER_LEFT
        }

        // 그룹은 한 명이 나가도 남은 사람들의 대화가 이어져야 한다(멤버 이탈은 별도 트랙).
        "그룹 방은 이 경로로 끝낼 수 없다" {
            val room = chatRoomRepository.save(ChatRoomFixture.group(sourceId = 300L, now = FRIDAY))
            chatRoomMemberRepository.saveAll(
                listOf(1L, 2L, 3L).map { ChatRoomMember.of(roomId = room.id, memberId = it) },
            )

            shouldThrow<WarnException> {
                chatRoomEndService.endByUser(room.id, memberId = 1L, now = FRIDAY)
            }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER

            chatRoomRepository.findAll().first().status shouldBe ChatRoomStatus.ACTIVE
        }
    }

    "방 나가기" - {
        fun saveGroupRoomWithMembers(vararg memberIds: Long) =
            chatRoomRepository.save(ChatRoomFixture.group(sourceId = 300L, now = FRIDAY)).also { room ->
                chatRoomMemberRepository.saveAll(memberIds.map { ChatRoomMember.of(roomId = room.id, memberId = it) })
            }

        "그룹에서 한 명이 나가면 나간 사실만 남고 방은 유지된다" {
            val room = saveGroupRoomWithMembers(1L, 2L, 3L)

            val result = chatRoomEndService.leave(room.id, memberId = 1L, now = FRIDAY)

            result.roomEnded shouldBe false
            result.systemMessages.size shouldBe 1
            result.systemMessages.first().content shouldBe ChatRoomEndService.MEMBER_LEFT
            // 조회자는 이 senderId 로 "○○님이 나갔습니다"를 렌더링한다
            result.systemMessages.first().senderId shouldBe 1L
            chatRoomRepository.findAll().first().status shouldBe ChatRoomStatus.ACTIVE
            chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 1L)?.hasLeft shouldBe true
        }

        "이탈로 잔여 인원이 1명이 되면 방을 해체한다" {
            val room = saveGroupRoomWithMembers(1L, 2L, 3L)
            chatRoomEndService.leave(room.id, memberId = 1L, now = FRIDAY)

            val result = chatRoomEndService.leave(room.id, memberId = 2L, now = FRIDAY)

            result.roomEnded shouldBe true
            result.systemMessages.map { it.content } shouldBe listOf(
                ChatRoomEndService.MEMBER_LEFT,
                ChatRoomEndService.INSUFFICIENT_MEMBERS,
            )
            // 해체 메시지의 senderId 는 마지막 이탈자다
            result.systemMessages.last().senderId shouldBe 2L

            val reloaded = chatRoomRepository.findAll().first()
            reloaded.isEnded shouldBe true
            reloaded.endReason shouldBe ChatEndReason.INSUFFICIENT_MEMBERS
        }

        "이미 나간 멤버가 다시 나가도 아무 것도 발행하지 않는다(멱등)" {
            val room = saveGroupRoomWithMembers(1L, 2L, 3L)
            chatRoomEndService.leave(room.id, memberId = 1L, now = FRIDAY)

            val result = chatRoomEndService.leave(room.id, memberId = 1L, now = FRIDAY)

            result.roomEnded shouldBe false
            result.systemMessages.size shouldBe 0
            chatMessageRepository.findAll().size shouldBe 1
        }

        "이미 종료된 그룹 방에서 나가도 아무 것도 발행하지 않는다(멱등)" {
            val room = saveGroupRoomWithMembers(1L, 2L, 3L)
            chatRoomEndService.endExpired(AFTER_EXPIRY)

            val result = chatRoomEndService.leave(room.id, memberId = 1L, now = AFTER_EXPIRY)

            result.roomEnded shouldBe false
            result.systemMessages.size shouldBe 0
            chatRoomRepository.findAll().first().endReason shouldBe ChatEndReason.EXPIRED
        }

        // 두 사람 방의 "나가기"는 종료와 같은 뜻이다 — FE 가 방 유형별로 호출을 가르지 않아도 된다.
        "1:1 방에서 나가면 종료와 동일하게 처리된다" {
            val room = saveRoomWithMembers(FRIDAY, 1L, 2L)

            val result = chatRoomEndService.leave(room.id, memberId = 1L, now = FRIDAY)

            result.roomEnded shouldBe true
            result.systemMessages.first().content shouldBe ChatRoomEndService.USER_LEFT
            chatRoomRepository.findAll().first().endReason shouldBe ChatEndReason.USER_ENDED
        }

        "재매칭 방에서 나가도 종료와 동일하게 처리된다" {
            val room = chatRoomRepository.save(ChatRoomFixture.rematch(sourceId = 200L, now = FRIDAY))
            chatRoomMemberRepository.saveAll(
                listOf(
                    ChatRoomMember.of(roomId = room.id, memberId = 1L),
                    ChatRoomMember.of(roomId = room.id, memberId = 2L),
                ),
            )

            val result = chatRoomEndService.leave(room.id, memberId = 1L, now = FRIDAY)

            result.roomEnded shouldBe true
            chatRoomRepository.findAll().first().endReason shouldBe ChatEndReason.USER_ENDED
        }

        "이미 종료된 1:1 방에서 나가도 아무 것도 발행하지 않는다(멱등)" {
            val room = saveRoomWithMembers(FRIDAY, 1L, 2L)
            chatRoomEndService.endByUser(room.id, memberId = 1L, now = FRIDAY)

            val result = chatRoomEndService.leave(room.id, memberId = 2L, now = FRIDAY)

            result.roomEnded shouldBe false
            result.systemMessages.size shouldBe 0
        }

        "방 멤버가 아니면 거부한다" {
            val room = saveGroupRoomWithMembers(1L, 2L, 3L)

            shouldThrow<WarnException> {
                chatRoomEndService.leave(room.id, memberId = 99L, now = FRIDAY)
            }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
        }
    }

    "종료 후 차단" - {
        "종료된 방에는 메시지를 보낼 수 없다" {
            val room = saveRoomWithMembers(FRIDAY, 1L, 2L)
            chatRoomEndService.endByUser(room.id, memberId = 1L, now = FRIDAY)

            shouldThrow<WarnException> {
                chatService.sendMessage(senderId = 2L, roomId = room.id, content = "아직 할 말이 남았어요")
            }.errorCode shouldBe ErrorCode.CHAT_ROOM_ENDED
        }

        "종료된 방에는 이미지 업로드 URL 을 발급하지 않는다" {
            val room = saveRoomWithMembers(FRIDAY, 1L, 2L)
            chatRoomEndService.endByUser(room.id, memberId = 1L, now = FRIDAY)

            shouldThrow<WarnException> {
                chatService.issueImageUploadUrls(
                    memberId = 2L,
                    roomId = room.id,
                    request = ChatImageUploadUrlsRequest(
                        files = listOf(ChatImageUploadFileRequest(contentType = "image/png", contentLength = 1024L)),
                    ),
                )
            }.errorCode shouldBe ErrorCode.CHAT_ROOM_ENDED
        }

        "종료된 방도 지난 대화는 읽을 수 있다" {
            val room = saveRoomWithMembers(FRIDAY, 1L, 2L)
            chatService.sendMessage(senderId = 1L, roomId = room.id, content = "반가워요")
            chatRoomEndService.endByUser(room.id, memberId = 1L, now = FRIDAY)

            val messages = chatService.getMessages(memberId = 2L, roomId = room.id, cursor = null, size = 30)

            // 보낸 메시지 + 종료 시스템 메시지
            messages.messages.size shouldBe 2
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

        // 각자 자기 멤버 행만 잠그면 두 명이 동시에 나갈 때 둘 다 "잔여 2명"을 읽어 아무도 해체하지
        // 않는다(1명짜리 방이 남는다). 방 행 잠금이 "이탈 기록 + 잔여 카운트 + 해체 판정"을 직렬화한다.
        "3명 방에서 두 명이 동시에 나가도 해체가 정확히 한 번 일어난다" {
            val room = chatRoomRepository.save(ChatRoomFixture.group(sourceId = 300L, now = FRIDAY))
            chatRoomMemberRepository.saveAll(
                listOf(1L, 2L, 3L).map { ChatRoomMember.of(roomId = room.id, memberId = it) },
            )
            val startLatch = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)

            val requests = listOf(1L, 2L).map { memberId ->
                executor.submit {
                    startLatch.await()
                    chatRoomEndService.leave(room.id, memberId, FRIDAY)
                }
            }
            startLatch.countDown()
            requests.forEach { it.get() }
            executor.shutdown()

            val reloaded = chatRoomRepository.findAll().first()
            reloaded.isEnded shouldBe true
            reloaded.endReason shouldBe ChatEndReason.INSUFFICIENT_MEMBERS
            // MEMBER_LEFT 2건 + INSUFFICIENT_MEMBERS 1건 — 해체 메시지가 중복 발행되지 않는다
            val contents = chatMessageRepository.findAll().map { it.content }
            contents.count { it == ChatRoomEndService.MEMBER_LEFT } shouldBe 2
            contents.count { it == ChatRoomEndService.INSUFFICIENT_MEMBERS } shouldBe 1
        }
    }
})
