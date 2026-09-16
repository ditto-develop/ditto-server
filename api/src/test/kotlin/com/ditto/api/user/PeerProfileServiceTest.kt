package com.ditto.api.user

import com.ditto.api.support.IntegrationTest
import com.ditto.api.user.service.PeerProfileService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewAnswerContent
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * 타인 프로필 보조 정보(평가·답변 일치)의 열람 권한과 비교 기준을 검증한다.
 * 권한 규칙 자체는 공개 프로필과 공유하므로, 여기서는 "보조 정보에도 같은 규칙이 걸리는가"를 본다.
 */
class PeerProfileServiceTest(
    private val peerProfileService: PeerProfileService,
    private val memberRepository: MemberRepository,
    private val memberBlockRepository: MemberBlockRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMember(nickname: String) =
        memberRepository.save(Member(nickname = nickname).apply { activate() })

    fun matchAccepted(viewerId: Long, targetId: Long) {
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = viewerId,
                receiverId = targetId,
                status = PersonalMatchStatus.ACCEPTED,
            ),
        )
    }

    fun completeQuizSet(memberId: Long, quizSetId: Long, quizIds: List<Long>, choiceIds: List<Long>) {
        val progress = QuizProgressFixture.create(
            memberId = memberId,
            quizSetId = quizSetId,
            totalCount = quizIds.size,
        )
        repeat(quizIds.size) { progress.recordAnswer() }
        quizProgressRepository.save(progress)
        quizIds.forEachIndexed { index, quizId ->
            quizAnswerRepository.save(
                QuizAnswerFixture.create(memberId = memberId, quizId = quizId, choiceId = choiceIds[index]),
            )
        }
    }

    /** 이번 운영 주 1:1 퀴즈셋을 완주시키고, 두 사람을 서로의 후보로 노출한다(성사 전 관계). */
    fun exposeAsCandidates(viewerId: Long, targetId: Long) {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek(matchingType = MatchingType.ONE_TO_ONE))
        val progress = QuizProgressFixture.create(memberId = viewerId, quizSetId = quizSet.id, totalCount = 1)
        progress.recordAnswer() // NOT_STARTED -> COMPLETED
        quizProgressRepository.save(progress)

        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = viewerId, otherMemberId = targetId, quizSetId = quizSet.id),
        )
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = targetId, otherMemberId = viewerId, quizSetId = quizSet.id),
        )
    }

    var reviewerSequence = 0

    /** 다른 회원이 [reviewedMemberId]에게 남긴 확정 평가 하나를 만든다. */
    fun saveReceivedAnswer(reviewedMemberId: Long, meetingStatus: MeetingStatus, rating: Int, comment: String?) {
        val author = saveMember("평가자${reviewerSequence++}")
        val review = memberReviewRepository.save(MemberReviewFixture.create(authorMemberId = author.id))
        val answer = reviewAnswerRepository.save(
            ReviewAnswerFixture.pending(memberReviewId = review.id, reviewedMemberId = reviewedMemberId),
        )
        answer.answer(ReviewAnswerContent.of(meetingStatus, rating, comment), LocalDateTime.now())
        reviewAnswerRepository.save(answer)
    }

    /** 공개 기준(3건)을 넘기는 평가를 쌓는다 — 노쇼 1건과 코멘트가 섞여 있다. */
    fun givePublicRatings(targetId: Long) {
        saveReceivedAnswer(targetId, MeetingStatus.MET, rating = 5, comment = "대화가 즐거웠어요")
        saveReceivedAnswer(targetId, MeetingStatus.MET, rating = 4, comment = "시간 약속을 잘 지켜요")
        saveReceivedAnswer(targetId, MeetingStatus.NO_SHOW, rating = 3, comment = null)
    }

    "열람 권한" - {
        "매칭되지 않은 상대의 평가는 볼 수 없다" {
            val viewer = saveMember("권한없는조회자")
            val target = saveMember("남남")

            val exception = shouldThrow<WarnException> {
                peerProfileService.getRatings(viewer.id, target.id)
            }
            exception.errorCode shouldBe ErrorCode.FORBIDDEN
        }

        "차단한 상대는 매칭 이력이 있어도 평가·답변 비교를 볼 수 없다" {
            val viewer = saveMember("차단한조회자")
            val target = saveMember("차단된대상")
            matchAccepted(viewer.id, target.id)
            memberBlockRepository.save(MemberBlock.create(viewer.id, target.id))

            shouldThrow<WarnException> { peerProfileService.getRatings(viewer.id, target.id) }
            shouldThrow<WarnException> { peerProfileService.getAnswerMatch(viewer.id, target.id) }
        }

        "이번 주 매칭 후보는 성사 전에도 평가·답변 비교를 볼 수 있다" {
            val viewer = saveMember("후보조회자")
            val target = saveMember("후보대상")
            exposeAsCandidates(viewer.id, target.id)

            // 403 이 아니어야 한다 — 후보 프로필 화면이 쓰는 구간이다.
            peerProfileService.getRatings(viewer.id, target.id).totalCount shouldBe 0
            peerProfileService.getAnswerMatch(viewer.id, target.id).totalCount shouldBe 0
        }

        "매칭 후보라도 차단 관계면 볼 수 없다" {
            val viewer = saveMember("차단한후보조회자")
            val target = saveMember("차단된후보대상")
            exposeAsCandidates(viewer.id, target.id)
            memberBlockRepository.save(MemberBlock.create(viewer.id, target.id))

            shouldThrow<WarnException> { peerProfileService.getRatings(viewer.id, target.id) }
            shouldThrow<WarnException> { peerProfileService.getAnswerMatch(viewer.id, target.id) }
        }

        "상대가 나를 차단한 경우에도 볼 수 없다 (방향 무관)" {
            val viewer = saveMember("차단당한조회자")
            val target = saveMember("차단한대상")
            matchAccepted(viewer.id, target.id)
            memberBlockRepository.save(MemberBlock.create(target.id, viewer.id))

            shouldThrow<WarnException> { peerProfileService.getAnswerMatch(viewer.id, target.id) }
        }
    }

    "답변 일치 비교" - {
        "함께 완주한 가장 최근 퀴즈셋을 기준으로 센다" {
            val viewer = saveMember("조회자")
            val target = saveMember("대상")
            matchAccepted(viewer.id, target.id)

            val older = quizSetRepository.save(QuizSetFixture.create(category = "성격"))
            val olderQuizIds = listOf(quizRepository.save(QuizFixture.create(quizSetId = older.id)).id)
            completeQuizSet(viewer.id, older.id, olderQuizIds, listOf(1L))
            completeQuizSet(target.id, older.id, olderQuizIds, listOf(1L))

            val latest = quizSetRepository.save(QuizSetFixture.create(category = "취미"))
            val latestQuizIds = (1..2).map {
                quizRepository.save(QuizFixture.create(quizSetId = latest.id, displayOrder = it)).id
            }
            completeQuizSet(viewer.id, latest.id, latestQuizIds, listOf(1L, 2L))
            completeQuizSet(target.id, latest.id, latestQuizIds, listOf(1L, 7L))

            val result = peerProfileService.getAnswerMatch(viewer.id, target.id)

            result.quizSetId shouldBe latest.id
            result.matchedCount shouldBe 1
            result.totalCount shouldBe 2
            result.matchRate shouldBe 50.0
        }

        "함께 완주한 퀴즈셋이 없으면 빈 요약을 반환한다" {
            val viewer = saveMember("조회자2")
            val target = saveMember("대상2")
            matchAccepted(viewer.id, target.id)

            val result = peerProfileService.getAnswerMatch(viewer.id, target.id)

            result.quizSetId shouldBe null
            result.totalCount shouldBe 0
            result.matchRate shouldBe 0.0
        }

        "문항이 하나도 없는 퀴즈셋이면 빈 요약을 반환한다" {
            val viewer = saveMember("조회자3")
            val target = saveMember("대상3")
            matchAccepted(viewer.id, target.id)

            // 문항 없이 완주 처리된 퀴즈셋 — 비교할 문항이 없다.
            val emptyQuizSet = quizSetRepository.save(QuizSetFixture.create())
            completeQuizSet(viewer.id, emptyQuizSet.id, emptyList(), emptyList())
            completeQuizSet(target.id, emptyQuizSet.id, emptyList(), emptyList())

            val result = peerProfileService.getAnswerMatch(viewer.id, target.id)

            result.quizSetId shouldBe null
            result.totalCount shouldBe 0
        }
    }

    "받은 평가" - {
        "매칭된 상대의 평가는 내 평가 조회와 같은 공개 기준(3건)을 따른다" {
            val viewer = saveMember("조회자4")
            val target = saveMember("평가없는대상")
            matchAccepted(viewer.id, target.id)

            val result = peerProfileService.getRatings(viewer.id, target.id)

            result.totalCount shouldBe 0
            result.publicThreshold shouldBe 3
            result.averageScore shouldBe 0.0
            result.ratings.isEmpty() shouldBe true
        }

        "매칭 성사된 상대는 코멘트와 노쇼 횟수까지 본다" {
            val viewer = saveMember("성사조회자")
            val target = saveMember("성사대상")
            matchAccepted(viewer.id, target.id)
            givePublicRatings(target.id)

            val result = peerProfileService.getRatings(viewer.id, target.id)

            result.totalCount shouldBe 3
            result.averageScore shouldBe 4.0
            result.noShowCount shouldBe 1
            result.ratings.size shouldBe 3
        }

        "성사 전 후보는 평균 점수와 건수까지만 본다 — 코멘트·노쇼는 가린다" {
            val viewer = saveMember("요약조회자")
            val target = saveMember("요약대상")
            exposeAsCandidates(viewer.id, target.id)
            givePublicRatings(target.id)

            val result = peerProfileService.getRatings(viewer.id, target.id)

            result.totalCount shouldBe 3
            result.averageScore shouldBe 4.0
            // 0 이 아니라 null 이다 — "노쇼 0회"라고 단언하지 않고 비공개임을 알린다.
            result.noShowCount shouldBe null
            result.ratings.isEmpty() shouldBe true
        }
    }
})
