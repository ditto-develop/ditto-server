package com.ditto.api.chat

import com.ditto.api.chat.dto.ChatVoteCastRequest
import com.ditto.api.chat.dto.ChatVoteCreateRequest
import com.ditto.api.chat.dto.ChatVoteCreateRequest.PlaceOptionRequest
import com.ditto.api.chat.dto.ChatVoteCreateRequest.TimeOptionRequest
import com.ditto.api.chat.service.ChatVoteService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.ChatVoteFixture
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatVoteStatus
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.chat.repository.ChatVoteChoiceRepository
import com.ditto.domain.chat.repository.ChatVoteOptionRepository
import com.ditto.domain.chat.repository.ChatVoteRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.sql.DataSource

private val FRIDAY = LocalDateTime.of(2026, 3, 13, 12, 0)
private val WEDNESDAY = LocalDateTime.of(2026, 3, 11, 9, 0)

class ChatVoteServiceTest(
    private val chatVoteService: ChatVoteService,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatVoteRepository: ChatVoteRepository,
    private val chatVoteOptionRepository: ChatVoteOptionRepository,
    private val chatVoteChoiceRepository: ChatVoteChoiceRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveGroupRoom(now: LocalDateTime = FRIDAY, vararg memberIds: Long): ChatRoom =
        chatRoomRepository.save(ChatRoomFixture.group(sourceId = 300L, now = now)).also { room ->
            memberIds.forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = it))
            }
        }

    fun createRequest(
        allowMultiple: Boolean = false,
        placeLabels: List<String> = listOf("성수 카페거리", "강남역"),
        meetAts: List<LocalDateTime> = listOf(
            LocalDateTime.of(2026, 3, 14, 19, 0),
            LocalDateTime.of(2026, 3, 15, 18, 0),
        ),
    ) = ChatVoteCreateRequest(
        allowMultiple = allowMultiple,
        placeOptions = placeLabels.map { PlaceOptionRequest(label = it) },
        timeOptions = meetAts.map { TimeOptionRequest(meetAt = it) },
    )

    "투표 생성" - {
        "생성하면 선택지가 저장되고 상세 형태로 돌아온다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)

            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())

            detail.voteId shouldNotBe 0L
            detail.status shouldBe ChatVoteStatus.OPEN
            detail.createdBy shouldBe 1L
            detail.totalMembers shouldBe 3
            detail.votedCount shouldBe 0
            detail.myVote shouldBe null
            detail.placeOptions.map { it.label } shouldBe listOf("성수 카페거리", "강남역")
            detail.timeOptions.size shouldBe 2
            chatVoteOptionRepository.findAll().size shouldBe 4
        }

        "이미 열린 투표가 있으면 VOTE_ALREADY_EXISTS 로 거부한다 — 그것을 돌려주지 않는다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())

            shouldThrow<WarnException> {
                chatVoteService.createVote(room.id, memberId = 2L, request = createRequest())
            }.errorCode shouldBe ErrorCode.VOTE_ALREADY_EXISTS
        }

        "마감된 투표만 있으면 새로 만들 수 있다 — 닫힌 투표는 방당 1개 제약에서 빠진다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val first = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            chatVoteRepository.findById(first.voteId).get().let {
                it.close(by = 1L, at = FRIDAY.plusHours(1))
                chatVoteRepository.save(it)
            }

            val second = chatVoteService.createVote(room.id, memberId = 2L, request = createRequest())

            second.voteId shouldNotBe first.voteId
            chatVoteRepository.findAllByRoomIdOrderByIdDesc(room.id).size shouldBe 2
        }

        "그룹이 아닌 방이면 GROUP_ROOM_ONLY 로 거부한다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 100L, now = FRIDAY))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L))

            shouldThrow<WarnException> {
                chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            }.errorCode shouldBe ErrorCode.GROUP_ROOM_ONLY
        }

        // 이탈은 행 삭제가 아니라 left_at 이라 exists 검사로는 나간 멤버가 통과한다 — hasLeft 로 막는다.
        "방을 나간 멤버는 투표를 만들 수 없다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 3L)!!
                .apply { leave(FRIDAY) }
                .let { chatRoomMemberRepository.save(it) }

            shouldThrow<WarnException> {
                chatVoteService.createVote(room.id, memberId = 3L, request = createRequest())
            }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
        }

        "개방 전 방이면 CHAT_ROOM_NOT_OPENED 로 거부한다" {
            val room = saveGroupRoom(WEDNESDAY, 1L, 2L, 3L)

            shouldThrow<WarnException> {
                chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            }.errorCode shouldBe ErrorCode.CHAT_ROOM_NOT_OPENED
        }

        "종료된 방이면 CHAT_ROOM_ENDED 로 거부한다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            room.expire(LocalDateTime.of(2026, 3, 16, 0, 0))
            chatRoomRepository.save(room)

            shouldThrow<WarnException> {
                chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            }.errorCode shouldBe ErrorCode.CHAT_ROOM_ENDED
        }

        "요청 안에 같은 상호명이 두 번 있으면 DUPLICATE_VOTE_OPTION 으로 거부한다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)

            shouldThrow<WarnException> {
                chatVoteService.createVote(
                    room.id, memberId = 1L,
                    request = createRequest(placeLabels = listOf("성수 카페거리", "성수 카페거리")),
                )
            }.errorCode shouldBe ErrorCode.DUPLICATE_VOTE_OPTION
        }

        "요청 안에 분 단위로 같은 일시가 두 번 있으면 DUPLICATE_VOTE_OPTION 으로 거부한다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val meetAt = LocalDateTime.of(2026, 3, 14, 19, 0)

            shouldThrow<WarnException> {
                chatVoteService.createVote(
                    room.id, memberId = 1L,
                    request = createRequest(meetAts = listOf(meetAt, meetAt.plusSeconds(30))),
                )
            }.errorCode shouldBe ErrorCode.DUPLICATE_VOTE_OPTION
        }
    }

    "cast — 투표하기 (치환)" - {
        "첫 투표는 표를 만들고 votedCount·myVote 가 채워진다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            val placeId = detail.placeOptions[0].optionId
            val timeId = detail.timeOptions[0].optionId

            val cast = chatVoteService.cast(
                room.id, detail.voteId, memberId = 2L,
                request = ChatVoteCastRequest(placeIds = listOf(placeId), timeIds = listOf(timeId)),
            )

            cast.votedCount shouldBe 1
            cast.myVote?.placeIds shouldBe listOf(placeId)
            cast.placeOptions[0].voterIds shouldBe listOf(2L)
        }

        // 재투표 화면이 기존 선택을 유지한 채 재제출한다 — 겹치는 표가 정상 경로다.
        "재투표는 치환이다 — 겹치는 표를 유지한 채 빠진 것만 지우고 새 것만 넣는다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(
                room.id, memberId = 1L, request = createRequest(allowMultiple = true),
            )
            val (placeA, placeB) = detail.placeOptions.map { it.optionId }
            val timeA = detail.timeOptions[0].optionId
            chatVoteService.cast(
                room.id, detail.voteId, memberId = 2L,
                request = ChatVoteCastRequest(placeIds = listOf(placeA), timeIds = listOf(timeA)),
            )

            // placeA 유지 + placeB 추가 + timeA 제거
            val recast = chatVoteService.cast(
                room.id, detail.voteId, memberId = 2L,
                request = ChatVoteCastRequest(placeIds = listOf(placeA, placeB), timeIds = emptyList()),
            )

            recast.myVote?.placeIds?.toSet() shouldBe setOf(placeA, placeB)
            recast.myVote?.timeIds shouldBe emptyList<Long>()
            chatVoteChoiceRepository.findAllByVoteId(detail.voteId).size shouldBe 2
        }

        "빈 요청은 내 표 전체 취소다 — myVote 가 null 로 돌아간다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            val placeId = detail.placeOptions[0].optionId
            chatVoteService.cast(
                room.id, detail.voteId, memberId = 2L,
                request = ChatVoteCastRequest(placeIds = listOf(placeId)),
            )

            val cancelled = chatVoteService.cast(
                room.id, detail.voteId, memberId = 2L, request = ChatVoteCastRequest(),
            )

            cancelled.myVote shouldBe null
            cancelled.votedCount shouldBe 0
        }

        "다른 투표의 선택지 ID 면 INVALID_VOTE_OPTION 이다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())

            shouldThrow<WarnException> {
                chatVoteService.cast(
                    room.id, detail.voteId, memberId = 2L,
                    request = ChatVoteCastRequest(placeIds = listOf(99999L)),
                )
            }.errorCode shouldBe ErrorCode.INVALID_VOTE_OPTION
        }

        "시간 선택지를 placeIds 에 실으면 INVALID_VOTE_OPTION 이다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            val timeId = detail.timeOptions[0].optionId

            shouldThrow<WarnException> {
                chatVoteService.cast(
                    room.id, detail.voteId, memberId = 2L,
                    request = ChatVoteCastRequest(placeIds = listOf(timeId)),
                )
            }.errorCode shouldBe ErrorCode.INVALID_VOTE_OPTION
        }

        "복수 선택이 꺼져 있으면 유형별 2개 이상은 VOTE_MULTIPLE_NOT_ALLOWED 다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(
                room.id, memberId = 1L, request = createRequest(allowMultiple = false),
            )
            val placeIds = detail.placeOptions.map { it.optionId }

            shouldThrow<WarnException> {
                chatVoteService.cast(
                    room.id, detail.voteId, memberId = 2L,
                    request = ChatVoteCastRequest(placeIds = placeIds),
                )
            }.errorCode shouldBe ErrorCode.VOTE_MULTIPLE_NOT_ALLOWED
        }

        "마감된 투표에는 던질 수 없다 — VOTE_ALREADY_CLOSED" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            chatVoteRepository.findById(detail.voteId).get().let {
                it.close(by = 1L, at = FRIDAY.plusHours(1))
                chatVoteRepository.save(it)
            }

            shouldThrow<WarnException> {
                chatVoteService.cast(
                    room.id, detail.voteId, memberId = 2L,
                    request = ChatVoteCastRequest(placeIds = listOf(detail.placeOptions[0].optionId)),
                )
            }.errorCode shouldBe ErrorCode.VOTE_ALREADY_CLOSED
        }

        "방을 나간 멤버는 던질 수 없다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 2L)!!
                .apply { leave(FRIDAY) }
                .let { chatRoomMemberRepository.save(it) }

            shouldThrow<WarnException> {
                chatVoteService.cast(
                    room.id, detail.voteId, memberId = 2L,
                    request = ChatVoteCastRequest(placeIds = listOf(detail.placeOptions[0].optionId)),
                )
            }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
        }

        "종료된 방에서는 던질 수 없다 — CHAT_ROOM_ENDED" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            room.expire(LocalDateTime.of(2026, 3, 16, 0, 0))
            chatRoomRepository.save(room)

            shouldThrow<WarnException> {
                chatVoteService.cast(
                    room.id, detail.voteId, memberId = 2L,
                    request = ChatVoteCastRequest(placeIds = listOf(detail.placeOptions[0].optionId)),
                )
            }.errorCode shouldBe ErrorCode.CHAT_ROOM_ENDED
        }

        // 투표 행 잠금이 치환 구간을 직렬화한다 — 겹치면 삭제·삽입 사이의 중간 상태가 셀 수 있다.
        "세 명이 동시에 던져도 표가 정확히 수렴한다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            val placeId = detail.placeOptions[0].optionId
            val timeId = detail.timeOptions[0].optionId
            val startLatch = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(3)

            val requests = listOf(1L, 2L, 3L).map { memberId ->
                executor.submit {
                    startLatch.await()
                    chatVoteService.cast(
                        room.id, detail.voteId, memberId,
                        request = ChatVoteCastRequest(placeIds = listOf(placeId), timeIds = listOf(timeId)),
                    )
                }
            }
            startLatch.countDown()
            requests.forEach { it.get() }
            executor.shutdown()

            val reloaded = chatVoteService.getVote(room.id, detail.voteId, memberId = 1L)
            reloaded.votedCount shouldBe 3
            reloaded.placeOptions[0].voterIds.toSet() shouldBe setOf(1L, 2L, 3L)
        }
    }

    "조회" - {
        "상세는 선택지별 투표자와 내 표를 담는다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            val placeId = detail.placeOptions[0].optionId
            val timeId = detail.timeOptions[0].optionId
            chatVoteChoiceRepository.save(ChatVoteFixture.choice(detail.voteId, placeId, memberId = 1L))
            chatVoteChoiceRepository.save(ChatVoteFixture.choice(detail.voteId, timeId, memberId = 1L))
            chatVoteChoiceRepository.save(ChatVoteFixture.choice(detail.voteId, placeId, memberId = 2L))

            val reloaded = chatVoteService.getVote(room.id, detail.voteId, memberId = 1L)

            reloaded.votedCount shouldBe 2
            reloaded.placeOptions[0].voterIds.toSet() shouldBe setOf(1L, 2L)
            reloaded.myVote?.placeIds shouldBe listOf(placeId)
            reloaded.myVote?.timeIds shouldBe listOf(timeId)
            // 2L 은 표는 던졌지만 조회자가 아니다 — myVote 는 조회자 기준이다
            chatVoteService.getVote(room.id, detail.voteId, memberId = 3L).myVote shouldBe null
        }

        // 이탈자 정책 — 조회는 되고(읽기 전용), 표는 집계에서 빠진다(분자·분모가 함께 준다).
        "이탈자도 결과를 읽을 수 있고, 이탈자의 표는 집계에서 빠진다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            val placeId = detail.placeOptions[0].optionId
            chatVoteChoiceRepository.save(ChatVoteFixture.choice(detail.voteId, placeId, memberId = 3L))
            chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 3L)!!
                .apply { leave(FRIDAY.plusHours(1)) }
                .let { chatRoomMemberRepository.save(it) }

            val seenByLeaver = chatVoteService.getVote(room.id, detail.voteId, memberId = 3L)
            val seenByStayer = chatVoteService.getVote(room.id, detail.voteId, memberId = 1L)

            seenByStayer.totalMembers shouldBe 2
            seenByStayer.votedCount shouldBe 0
            seenByStayer.placeOptions[0].voterIds shouldBe emptyList()
            // 이탈자 본인도 같은 집계를 본다 — 자기 표가 빠진 결과라 myVote 도 null 이다
            seenByLeaver.myVote shouldBe null
        }

        "목록은 최신 투표가 앞이다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val first = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())
            chatVoteRepository.findById(first.voteId).get().let {
                it.close(by = 1L, at = FRIDAY.plusHours(1))
                chatVoteRepository.save(it)
            }
            val second = chatVoteService.createVote(room.id, memberId = 2L, request = createRequest())

            chatVoteService.getVotes(room.id, memberId = 1L).map { it.voteId } shouldBe
                listOf(second.voteId, first.voteId)
        }

        "다른 방의 투표 ID 로 상세를 조회하면 VOTE_NOT_FOUND 다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)
            val other = chatRoomRepository.save(ChatRoomFixture.group(sourceId = 400L, now = FRIDAY))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = other.id, memberId = 1L))
            val detail = chatVoteService.createVote(room.id, memberId = 1L, request = createRequest())

            shouldThrow<WarnException> {
                chatVoteService.getVote(other.id, detail.voteId, memberId = 1L)
            }.errorCode shouldBe ErrorCode.VOTE_NOT_FOUND
        }

        "방 멤버가 아니면 조회할 수 없다" {
            val room = saveGroupRoom(FRIDAY, 1L, 2L, 3L)

            shouldThrow<WarnException> {
                chatVoteService.getVotes(room.id, memberId = 99L)
            }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
        }
    }
})
