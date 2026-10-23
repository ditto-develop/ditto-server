package com.ditto.domain.review.repository

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.ReviewAnswerContent
import com.ditto.domain.review.entity.ReviewProgressStatus
import com.ditto.domain.review.repository.querydsl.MemberReviewCount
import com.ditto.domain.review.repository.querydsl.MutualRatingCount
import com.ditto.domain.review.repository.querydsl.RatingWithMatchScore
import com.ditto.domain.review.repository.querydsl.ReviewAnswerCount
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

private val WEEK = LocalDate.of(2026, 10, 19)
private val ANSWERED_AT = LocalDateTime.of(2026, 10, 26, 10, 0)

class MemberReviewStatsQueryTest(
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val memberRepository: MemberRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var memberIndex = 0
    var chatRoomId = 0L

    fun saveMember(gender: Gender = Gender.MALE, role: MemberRole = MemberRole.USER): Long {
        memberIndex += 1
        val nickname = "m$memberIndex"
        return memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", gender = gender, role = role),
        ).id
    }

    fun saveReview(
        authorMemberId: Long,
        matchType: ChatRoomType = ChatRoomType.PERSONAL,
        weekStartedOn: LocalDate = WEEK,
    ): MemberReview {
        chatRoomId += 1
        return memberReviewRepository.save(
            MemberReviewFixture.create(
                authorMemberId = authorMemberId,
                matchType = matchType,
                chatRoomId = chatRoomId,
                weekStartedOn = weekStartedOn,
            ),
        )
    }

    // 대상 하나에 답하고 평가지를 끝낸다.
    fun answer(review: MemberReview, meetingStatus: MeetingStatus, rating: Int, hasRemainingTarget: Boolean = false) {
        val answer = ReviewAnswerFixture.pending(memberReviewId = review.id, reviewedMemberId = 999L)
        answer.answer(ReviewAnswerContent.of(meetingStatus, rating, null), ANSWERED_AT)
        reviewAnswerRepository.save(answer)
        review.recordAnswer(hasRemainingTarget, ANSWERED_AT)
        memberReviewRepository.save(review)
    }

    "countMemberReviewsInWeek" - {
        "그 주 평가지를 유형·상태로 묶고 관리자·다른 주는 뺀다" {
            answer(saveReview(saveMember()), MeetingStatus.MET, 5)
            answer(saveReview(saveMember(), ChatRoomType.GROUP), MeetingStatus.CHAT_ONLY, 3, hasRemainingTarget = true)
            saveReview(saveMember())
            saveReview(saveMember(role = MemberRole.ADMIN))
            saveReview(saveMember(), weekStartedOn = WEEK.minusWeeks(1))

            val counts = memberReviewRepository.countMemberReviewsInWeek(WEEK, null, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                MemberReviewCount(ChatRoomType.PERSONAL, ReviewProgressStatus.COMPLETED, 1L),
                MemberReviewCount(ChatRoomType.PERSONAL, ReviewProgressStatus.NOT_STARTED, 1L),
                MemberReviewCount(ChatRoomType.GROUP, ReviewProgressStatus.IN_PROGRESS, 1L),
            )
        }

        "유형과 쓴 사람 조건을 건다" {
            saveReview(saveMember(Gender.FEMALE))
            saveReview(saveMember(Gender.MALE))
            saveReview(saveMember(Gender.FEMALE), ChatRoomType.GROUP)

            val counts = memberReviewRepository.countMemberReviewsInWeek(
                WEEK,
                ChatRoomType.PERSONAL,
                MemberFilter(gender = Gender.FEMALE),
            )

            counts shouldContainExactlyInAnyOrder listOf(
                MemberReviewCount(ChatRoomType.PERSONAL, ReviewProgressStatus.NOT_STARTED, 1L),
            )
        }
    }

    "countAnsweredInWeek" - {
        "확정된 답만 유형·만남 상태·별점으로 묶는다" {
            answer(saveReview(saveMember()), MeetingStatus.MET, 5)
            answer(saveReview(saveMember()), MeetingStatus.MET, 5)
            answer(saveReview(saveMember(), ChatRoomType.GROUP), MeetingStatus.NO_SHOW, 1)
            reviewAnswerRepository.save(ReviewAnswerFixture.pending(memberReviewId = saveReview(saveMember()).id))
            answer(saveReview(saveMember(role = MemberRole.ADMIN)), MeetingStatus.MET, 4)

            val counts = reviewAnswerRepository.countAnsweredInWeek(WEEK, null, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                ReviewAnswerCount(ChatRoomType.PERSONAL, MeetingStatus.MET, 5, 2L),
                ReviewAnswerCount(ChatRoomType.GROUP, MeetingStatus.NO_SHOW, 1, 1L),
            )
        }
    }

    // 한 방의 평가지 하나에서 대상마다 별점을 남긴다.
    fun rateInRoom(
        roomId: Long,
        authorMemberId: Long,
        ratingsByTarget: Map<Long, Int>,
        matchType: ChatRoomType = ChatRoomType.PERSONAL,
        matchId: Long = 1L,
        quizSetId: Long = 1L,
    ) {
        val review = memberReviewRepository.save(
            MemberReviewFixture.create(
                authorMemberId = authorMemberId,
                matchType = matchType,
                matchId = matchId,
                chatRoomId = roomId,
                quizSetId = quizSetId,
                weekStartedOn = WEEK,
            ),
        )
        ratingsByTarget.forEach { (targetId, rating) ->
            val answer = ReviewAnswerFixture.pending(memberReviewId = review.id, reviewedMemberId = targetId)
            answer.answer(ReviewAnswerContent.of(MeetingStatus.MET, rating, null), ANSWERED_AT)
            reviewAnswerRepository.save(answer)
        }
    }

    "countMutualRatingsInWeek" - {
        "같은 방에서 서로 답한 쌍만 작성자 id 가 작은 쪽에서 한 번 센다" {
            val (a, b, c, d) = List(4) { saveMember() }
            rateInRoom(101L, a, mapOf(b to 5))
            rateInRoom(101L, b, mapOf(a to 4))
            rateInRoom(102L, c, mapOf(d to 5))
            val (e, f, g) = List(3) { saveMember() }
            rateInRoom(103L, e, mapOf(f to 4, g to 5), ChatRoomType.GROUP)
            rateInRoom(103L, f, mapOf(e to 2), ChatRoomType.GROUP)
            rateInRoom(103L, g, mapOf(e to 5), ChatRoomType.GROUP)

            reviewAnswerRepository.countMutualRatingsInWeek(WEEK, null) shouldContainExactlyInAnyOrder listOf(
                MutualRatingCount(ChatRoomType.PERSONAL, 5, 4, 1L),
                MutualRatingCount(ChatRoomType.GROUP, 4, 2, 1L),
                MutualRatingCount(ChatRoomType.GROUP, 5, 5, 1L),
            )
            reviewAnswerRepository.countMutualRatingsInWeek(WEEK, ChatRoomType.PERSONAL) shouldContainExactlyInAnyOrder
                listOf(MutualRatingCount(ChatRoomType.PERSONAL, 5, 4, 1L))
        }
    }

    "findRatingsWithMatchScoreInWeek" - {
        "1:1 은 작성자 → 대상 후보 점수, 그룹은 그룹 점수를 붙이고 후보 행이 없으면 null 이다" {
            val (a, b, c) = List(3) { saveMember() }
            matchCandidateRepository.save(MatchCandidateFixture.create(a, b, quizSetId = 7L, score = 70.0))
            rateInRoom(201L, a, mapOf(b to 5), quizSetId = 7L)
            rateInRoom(202L, c, mapOf(a to 2), quizSetId = 7L)
            val groupMatch = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 8L, score = 66.0))
            rateInRoom(203L, b, mapOf(c to 4), ChatRoomType.GROUP, matchId = groupMatch.id, quizSetId = 8L)

            reviewAnswerRepository.findRatingsWithMatchScoreInWeek(WEEK, null) shouldContainExactlyInAnyOrder listOf(
                RatingWithMatchScore(ChatRoomType.PERSONAL, 5, 70.0),
                RatingWithMatchScore(ChatRoomType.PERSONAL, 2, null),
                RatingWithMatchScore(ChatRoomType.GROUP, 4, 66.0),
            )
        }
    }

    "준 별점 평균" - {
        "회원마다 준 별점을 평균 내고 다음 회차에 완주한 회원만 따로 준다" {
            val againWeek = WEEK.plusWeeks(1)
            val againSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = againWeek.atStartOfDay(),
                    endDate = againWeek.plusDays(2).atTime(23, 59, 59),
                ),
            )
            val (satisfied, unsatisfied, target, other) = List(4) { saveMember() }
            rateInRoom(301L, satisfied, mapOf(target to 5, other to 3), ChatRoomType.GROUP)
            rateInRoom(302L, unsatisfied, mapOf(target to 2))
            rateInRoom(303L, saveMember(role = MemberRole.ADMIN), mapOf(target to 1))
            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = unsatisfied, quizSetId = againSet.id, totalCount = 1)
                    .apply { recordAnswer() },
            )

            reviewAnswerRepository.findAverageGivenRatingsInWeek(
                WEEK,
                null,
                MemberFilter.NONE,
            ) shouldContainExactlyInAnyOrder listOf(4.0, 2.0)
            reviewAnswerRepository.findAverageGivenRatingsOfMembersCompletedAgain(
                WEEK,
                againWeek,
                null,
                MemberFilter.NONE,
            ) shouldContainExactlyInAnyOrder listOf(2.0)
        }
    }
})
