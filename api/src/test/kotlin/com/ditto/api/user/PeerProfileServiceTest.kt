package com.ditto.api.user

import com.ditto.api.support.IntegrationTest
import com.ditto.api.user.service.PeerProfileService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
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
    }
})
