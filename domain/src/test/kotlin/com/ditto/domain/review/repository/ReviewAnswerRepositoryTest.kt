package com.ditto.domain.review.repository

import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewAnswer
import com.ditto.domain.review.entity.ReviewAnswerContent
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private val AFTER_EXPIRY = LocalDateTime.of(2026, 3, 16, 0, 0)
private const val REVIEWED_MEMBER_ID = 2L

class ReviewAnswerRepositoryTest(
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val chatRoomRepository: ChatRoomRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var nextGroupMatchId = 1L

    fun saveRoom(ended: Boolean): ChatRoom {
        val sourceId = nextGroupMatchId++
        val room = if (ended) ChatRoomFixture.endedGroup(sourceId) else ChatRoomFixture.group(sourceId)
        return chatRoomRepository.save(room)
    }

    fun saveAnswer(
        room: ChatRoom,
        authorMemberId: Long = 1L,
        reviewedMemberId: Long = REVIEWED_MEMBER_ID,
        meetingStatus: MeetingStatus = MeetingStatus.MET,
        answeredAt: LocalDateTime? = AFTER_EXPIRY,
    ): ReviewAnswer {
        val review = memberReviewRepository.save(
            MemberReviewFixture.create(authorMemberId = authorMemberId, chatRoomId = room.id),
        )
        val answer = ReviewAnswerFixture.pending(memberReviewId = review.id, reviewedMemberId = reviewedMemberId)
        answeredAt?.let { answer.answer(ReviewAnswerContent.of(meetingStatus, 4, null), it) }
        return reviewAnswerRepository.save(answer)
    }

    "findAllAnsweredInEndedRoomsByReviewedMemberId" - {
        "끝난 방의 확정 평가만 최신순으로 돌려준다" {
            val older = saveAnswer(saveRoom(ended = true), answeredAt = AFTER_EXPIRY)
            val newer = saveAnswer(saveRoom(ended = true), answeredAt = AFTER_EXPIRY.plusHours(1))
            saveAnswer(saveRoom(ended = true), answeredAt = null)

            reviewAnswerRepository.findAllAnsweredInEndedRoomsByReviewedMemberId(REVIEWED_MEMBER_ID)
                .map { it.id } shouldBe listOf(newer.id, older.id)
        }

        "다른 사람이 받은 평가는 섞이지 않는다" {
            saveAnswer(saveRoom(ended = true), reviewedMemberId = 3L)

            reviewAnswerRepository.findAllAnsweredInEndedRoomsByReviewedMemberId(REVIEWED_MEMBER_ID).size shouldBe 0
        }

        "아직 열린 방에서 나간 사람이 쓴 평가는 세지 않는다" {
            saveAnswer(saveRoom(ended = false))

            reviewAnswerRepository.findAllAnsweredInEndedRoomsByReviewedMemberId(REVIEWED_MEMBER_ID).size shouldBe 0
        }
    }

    "countAnsweredInEndedRoomsByReviewedMemberIdAndMeetingStatus" - {
        "끝난 방에서 그 만남 상태로 확정된 평가만 센다" {
            val endedRoom = saveRoom(ended = true)
            saveAnswer(endedRoom, authorMemberId = 1L, meetingStatus = MeetingStatus.MET)
            saveAnswer(endedRoom, authorMemberId = 3L, meetingStatus = MeetingStatus.CHAT_ONLY)
            saveAnswer(endedRoom, authorMemberId = 4L, meetingStatus = MeetingStatus.MET, answeredAt = null)
            saveAnswer(saveRoom(ended = false), meetingStatus = MeetingStatus.MET)

            reviewAnswerRepository.countAnsweredInEndedRoomsByReviewedMemberIdAndMeetingStatus(
                reviewedMemberId = REVIEWED_MEMBER_ID,
                meetingStatus = MeetingStatus.MET,
            ) shouldBe 1L
        }
    }
})
