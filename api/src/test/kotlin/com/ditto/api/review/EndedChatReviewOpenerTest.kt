package com.ditto.api.review

import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.review.dto.ReviewAnswerSubmitRequest
import com.ditto.api.review.service.EndedChatReviewOpener
import com.ditto.api.review.service.MemberReviewService
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.repository.MemberReviewRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private val FRIDAY = LocalDateTime.of(2026, 3, 13, 12, 0)
private val AFTER_EXPIRY = LocalDateTime.of(2026, 3, 16, 0, 0)
private const val MEMBER_A = 1L
private const val MEMBER_B = 2L
private const val MEMBER_C = 3L

class EndedChatReviewOpenerTest(
    private val endedChatReviewOpener: EndedChatReviewOpener,
    private val chatRoomEndService: ChatRoomEndService,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val quizSetRepository: QuizSetRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val rematchRepository: RematchRepository,
    private val memberReviewService: MemberReviewService,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    /** 방 → PersonalMatch → QuizSet 사슬을 갖춘 1:1 방을 만든다. quizSetId·weekStartedOn 이 그 사슬에서 나온다. */
    fun saveEndedPersonalChat(): Long {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = MEMBER_A,
                receiverId = MEMBER_B,
                quizSetId = quizSet.id,
                status = PersonalMatchStatus.ACCEPTED,
            ),
        )
        val room = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = match.id, now = FRIDAY))
        chatRoomMemberRepository.saveAll(
            listOf(
                ChatRoomMember.of(roomId = room.id, memberId = MEMBER_A),
                ChatRoomMember.of(roomId = room.id, memberId = MEMBER_B),
            ),
        )
        chatRoomEndService.endExpired(AFTER_EXPIRY)
        return room.id
    }

    /** 3명 그룹 채팅 → GroupMatch → QuizSet 사슬을 갖춘 종료된 방. */
    fun saveEndedGroupChat(vararg memberIds: Long): Long {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val match = groupMatchRepository.save(
            GroupMatchFixture.create(quizSetId = quizSet.id, isActive = true, participantCount = memberIds.size),
        )
        val room = chatRoomRepository.save(ChatRoomFixture.group(sourceId = match.id, now = FRIDAY))
        chatRoomMemberRepository.saveAll(memberIds.map { ChatRoomMember.of(roomId = room.id, memberId = it) })
        chatRoomEndService.endExpired(AFTER_EXPIRY)
        return room.id
    }

    "종료된 1:1 채팅으로 평가를 연다" - {
        "참여자마다 평가가 하나씩 열린다" {
            val roomId = saveEndedPersonalChat()

            endedChatReviewOpener.openFor(listOf(roomId))

            val reviews = memberReviewRepository.findAll()
            reviews.size shouldBe 2
            reviews.map { it.authorMemberId }.toSet() shouldBe setOf(MEMBER_A, MEMBER_B)
        }

        "원본 매칭에서 가져온 값이 평가에 담긴다" {
            val roomId = saveEndedPersonalChat()
            val expected = personalMatchRepository.findAll().first()

            endedChatReviewOpener.openFor(listOf(roomId))

            val review = memberReviewRepository.findAll().first()
            review.chatRoomId shouldBe roomId
            review.matchId shouldBe expected.id
            // chat_room 에 없어 PersonalMatch → QuizSet 을 타고 채운 값들
            review.quizSetId shouldBe expected.quizSetId
            review.weekStartedOn shouldBe quizSetRepository.findAll().first().weekStartedOn
        }

        "같은 방으로 다시 열어도 평가가 늘지 않는다(멱등)" {
            val roomId = saveEndedPersonalChat()

            endedChatReviewOpener.openFor(listOf(roomId))
            endedChatReviewOpener.openFor(listOf(roomId))

            memberReviewRepository.findAll().size shouldBe 2
        }
    }

    "누락 복구" - {
        "종료됐는데 평가가 없는 방을 찾아 연다" {
            saveEndedPersonalChat()
            // openFor 를 건너뛴 상태 = 종료 직후 생성이 실패했거나 그 사이 앱이 죽은 경우
            memberReviewRepository.findAll().size shouldBe 0

            endedChatReviewOpener.openMissing() shouldBe 1

            memberReviewRepository.findAll().size shouldBe 2
        }

        "평가가 이미 열린 방은 복구 대상이 아니다" {
            val roomId = saveEndedPersonalChat()
            endedChatReviewOpener.openFor(listOf(roomId))

            endedChatReviewOpener.openMissing() shouldBe 0

            memberReviewRepository.findAll().size shouldBe 2
        }

        "아직 끝나지 않은 방은 복구 대상이 아니다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val match = personalMatchRepository.save(PersonalMatchFixture.create(quizSetId = quizSet.id))
            val room = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = match.id, now = FRIDAY))
            chatRoomMemberRepository.saveAll(
                listOf(
                    ChatRoomMember.of(roomId = room.id, memberId = MEMBER_A),
                    ChatRoomMember.of(roomId = room.id, memberId = MEMBER_B),
                ),
            )

            endedChatReviewOpener.openMissing() shouldBe 0

            memberReviewRepository.findAll().size shouldBe 0
        }
    }

    "종료된 그룹 채팅으로 평가와 재매칭 쌍을 함께 만든다" - {
        "참여자마다 평가가 열린다" {
            saveEndedGroupChat(MEMBER_A, MEMBER_B, MEMBER_C)

            endedChatReviewOpener.openMissing() shouldBe 1

            memberReviewRepository.findAll().map { it.authorMemberId }.toSet() shouldBe
                setOf(MEMBER_A, MEMBER_B, MEMBER_C)
        }

        // 쌍이 없으면 RematchSubmitter 가 제출을 INVALID_REVIEW_TARGET 으로 거부한다.
        // 3명이면 3×2÷2 = 3쌍이고, 항상 (작은 ID, 큰 ID) 로 정규화된다.
        "참여자 전원의 비순서 쌍이 만들어진다" {
            saveEndedGroupChat(MEMBER_A, MEMBER_B, MEMBER_C)

            endedChatReviewOpener.openMissing()

            val pairs = rematchRepository.findAll()
            pairs.size shouldBe 3
            pairs.map { it.memberId1 to it.memberId2 }.toSet() shouldBe
                setOf(MEMBER_A to MEMBER_B, MEMBER_A to MEMBER_C, MEMBER_B to MEMBER_C)
        }

        "다시 열어도 쌍이 늘지 않는다(멱등)" {
            val roomId = saveEndedGroupChat(MEMBER_A, MEMBER_B, MEMBER_C)

            endedChatReviewOpener.openFor(listOf(roomId))
            endedChatReviewOpener.openFor(listOf(roomId))

            rematchRepository.findAll().size shouldBe 3
            memberReviewRepository.findAll().size shouldBe 3
        }

        // 쌍 생성이 왜 필요한지를 인과로 고정한다 — 쌍 개수만 세면 "그래서 뭐가 되는가"가 빠진다.
        // 그룹 평가는 재매칭 의사를 필수로 받고, RematchSubmitter 가 쌍을 못 찾으면 제출을 거부한다.
        "열린 평가를 곧바로 제출할 수 있다" {
            saveEndedGroupChat(MEMBER_A, MEMBER_B, MEMBER_C)
            endedChatReviewOpener.openMissing()

            val myReview = memberReviewRepository.findAll().first { it.authorMemberId == MEMBER_A }

            // 쌍이 없으면 여기서 INVALID_REVIEW_TARGET 으로 거부된다
            memberReviewService.submitAnswer(
                memberId = MEMBER_A,
                reviewId = myReview.id,
                reviewedMemberId = MEMBER_B,
                request = ReviewAnswerSubmitRequest(
                    meetingStatus = MeetingStatus.MET,
                    rating = 5,
                    wantsOneToOneRematch = true,
                ),
            )

            rematchRepository.findAll()
                .first { it.memberId1 == MEMBER_A && it.memberId2 == MEMBER_B }
                .wantsOf(MEMBER_A) shouldBe true
        }

        "1:1 방에는 쌍을 만들지 않는다" {
            val roomId = saveEndedPersonalChat()

            endedChatReviewOpener.openFor(listOf(roomId))

            rematchRepository.findAll().size shouldBe 0
            memberReviewRepository.findAll().size shouldBe 2
        }
    }

    "재매칭 방은 평가를 열지 않는다" - {
        // 관측 지점은 "평가가 0건"이 아니라 **복구 배치 슬롯을 차지하지 않는가**다.
        // 평가 0건은 필터가 없어도 성립해(원본이 group_match 가 아니라 조립이 실패한다) 아무것도 증명하지 못한다.
        //
        // 이 조회에서 빠지지 않으면 재매칭 방이 "끝났는데 평가 0건"으로 영원히 남아 매 주기 다시 잡히고,
        // 종료 시각 오름차순의 앞자리를 점유해 그 뒤에 끝난 방이 복구 대상에 들어오지 못한다.
        "누락 복구 조회에서 빠진다 — 정상 방만 남는다" {
            val personalRoomId = saveEndedPersonalChat()
            val rematchRoom = chatRoomRepository.save(ChatRoomFixture.rematch(sourceId = 500L, now = FRIDAY))
            chatRoomMemberRepository.saveAll(
                listOf(
                    ChatRoomMember.of(roomId = rematchRoom.id, memberId = MEMBER_A),
                    ChatRoomMember.of(roomId = rematchRoom.id, memberId = MEMBER_B),
                ),
            )
            chatRoomEndService.endExpired(AFTER_EXPIRY)

            memberReviewRepository.findEndedChatRoomIdsWithoutReview(100) shouldBe listOf(personalRoomId)
        }

        "종료해도 평가가 만들어지지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.rematch(sourceId = 500L, now = FRIDAY))
            chatRoomMemberRepository.saveAll(
                listOf(
                    ChatRoomMember.of(roomId = room.id, memberId = MEMBER_A),
                    ChatRoomMember.of(roomId = room.id, memberId = MEMBER_B),
                ),
            )
            chatRoomEndService.endExpired(AFTER_EXPIRY)

            endedChatReviewOpener.openFor(listOf(room.id))

            memberReviewRepository.findAll().size shouldBe 0
        }
    }

    "열 수 없는 방" - {
        // 탈퇴 hard delete 등으로 원본 매칭이 없으면 quizSetId·weekStartedOn 을 채울 수 없다.
        // 같은 배치의 정상 방까지 막지 않도록 건너뛴다(원인은 D1 에서 다룰 영역).
        "매칭을 찾을 수 없으면 건너뛰고 예외를 올리지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 9999L, now = FRIDAY))
            chatRoomMemberRepository.saveAll(
                listOf(
                    ChatRoomMember.of(roomId = room.id, memberId = MEMBER_A),
                    ChatRoomMember.of(roomId = room.id, memberId = MEMBER_B),
                ),
            )
            chatRoomEndService.endExpired(AFTER_EXPIRY)

            // 반환값은 "조회된 방 수"가 아니라 "평가가 실제로 열린 방 수"다 —
            // 조회 건수를 돌려주면 영영 열 수 없는 방을 매 주기 "복구 성공"으로 집계한다.
            endedChatReviewOpener.openMissing() shouldBe 0

            memberReviewRepository.findAll().size shouldBe 0
        }

        // 배치 전체를 한 트랜잭션으로 묶으면 이 방 하나가 앞서 성공한 방들까지 롤백시키고,
        // anti-join 이 매 주기 다시 집어오므로 복구가 영구히 막힌다.
        "열 수 없는 방이 섞여 있어도 나머지 방의 평가는 열린다" {
            val healthyRoomId = saveEndedPersonalChat()
            val brokenRoom = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 9999L, now = FRIDAY))
            chatRoomMemberRepository.saveAll(
                listOf(
                    ChatRoomMember.of(roomId = brokenRoom.id, memberId = MEMBER_A),
                    ChatRoomMember.of(roomId = brokenRoom.id, memberId = MEMBER_B),
                ),
            )
            chatRoomEndService.endExpired(AFTER_EXPIRY)

            endedChatReviewOpener.openMissing() shouldBe 1

            memberReviewRepository.findAll().map { it.chatRoomId }.toSet() shouldBe setOf(healthyRoomId)
        }

        // createReviews 가 참여자 0명일 때 WarnException(RuntimeException)을 던진다.
        // 배치 단위 트랜잭션이면 이게 poison pill 이 된다.
        "참여자가 없는 방이 섞여 있어도 나머지는 열린다" {
            val healthyRoomId = saveEndedPersonalChat()
            val quizSet = quizSetRepository.findAll().first()
            val emptyMatch = personalMatchRepository.save(
                PersonalMatchFixture.create(requesterId = 7L, receiverId = 8L, quizSetId = quizSet.id),
            )
            // 멤버 레코드를 만들지 않아 참여자 0명인 방
            chatRoomRepository.save(ChatRoomFixture.personal(sourceId = emptyMatch.id, now = FRIDAY))
            chatRoomEndService.endExpired(AFTER_EXPIRY)

            endedChatReviewOpener.openMissing() shouldBe 1

            memberReviewRepository.findAll().map { it.chatRoomId }.toSet() shouldBe setOf(healthyRoomId)
        }
    }
})
