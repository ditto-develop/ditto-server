package com.ditto.api.review

import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.review.service.EndedChatReviewOpener
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.review.repository.MemberReviewRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private val FRIDAY = LocalDateTime.of(2026, 3, 13, 12, 0)
private val AFTER_EXPIRY = LocalDateTime.of(2026, 3, 16, 0, 0)
private const val MEMBER_A = 1L
private const val MEMBER_B = 2L

class EndedChatReviewOpenerTest(
    private val endedChatReviewOpener: EndedChatReviewOpener,
    private val chatRoomEndService: ChatRoomEndService,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val quizSetRepository: QuizSetRepository,
    private val memberReviewRepository: MemberReviewRepository,
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

    "그룹 방은 건너뛴다" - {
        // 그룹은 멤버십 동결·재매칭 pair 가 얽혀 별도 트랙(I1G)이다.
        "종료된 그룹 방으로는 평가를 열지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.group(sourceId = 500L, now = FRIDAY))
            chatRoomMemberRepository.saveAll(
                listOf(
                    ChatRoomMember.of(roomId = room.id, memberId = MEMBER_A),
                    ChatRoomMember.of(roomId = room.id, memberId = MEMBER_B),
                ),
            )
            chatRoomEndService.endExpired(AFTER_EXPIRY)

            endedChatReviewOpener.openFor(listOf(room.id))
            endedChatReviewOpener.openMissing() shouldBe 0

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
