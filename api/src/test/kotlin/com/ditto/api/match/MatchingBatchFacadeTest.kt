package com.ditto.api.match

import com.ditto.api.match.service.MatchingBatchFacade
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.GenderPreference
import com.ditto.domain.member.entity.MemberStatus
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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class MatchingBatchFacadeTest(
    private val matchingBatchFacade: MatchingBatchFacade,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val groupMatchRepository: GroupMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        // 픽스처 퀴즈셋은 2026-04-12 23:59:59 에 끝난다 — 그 다음 목요일 05:00 배치 시각
        val batchAt = LocalDateTime.of(2026, 4, 16, 5, 0)

        /** 문항 하나짜리 퀴즈셋에 [nicknames] 회원이 모두 같은 답으로 완주한 상태를 만든다. 1:1 자격(활성 회원·성별·나이·선호)도 채운다. */
        fun saveEndedQuizSetCompletedBy(matchingType: MatchingType, nicknames: List<String>): Long {
            val quizSetId = quizSetRepository.save(QuizSetFixture.create(matchingType = matchingType)).id
            val quizId = quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 1)).id
            nicknames.forEach { nickname ->
                val memberId = memberRepository.save(MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE, gender = Gender.MALE, age = 25)).id
                quizAnswerRepository.save(QuizAnswerFixture.create(memberId = memberId, quizId = quizId, choiceId = 1L))
                val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
                progress.recordAnswer()
                progress.selectPreferredGender(GenderPreference.ANY)
                quizProgressRepository.save(progress)
            }
            return quizSetId
        }

        "runScheduledMatching" - {

            "마감된 퀴즈셋마다 후보를 만들고, 생성한 셋 ID만 돌려준다" {
                val oneToOneId = saveEndedQuizSetCompletedBy(MatchingType.ONE_TO_ONE, listOf("배치1:1A", "배치1:1B"))
                val groupId = saveEndedQuizSetCompletedBy(MatchingType.GROUP, listOf("배치그룹A", "배치그룹B", "배치그룹C"))
                // 아직 끝나지 않은 셋은 대상이 아니다
                val openId = quizSetRepository.save(
                    QuizSetFixture.create(endDate = LocalDateTime.of(2026, 4, 22, 23, 59, 59)),
                ).id

                val generatedIds = matchingBatchFacade.runScheduledMatching(batchAt)

                generatedIds.sorted() shouldBe listOf(oneToOneId, groupId).sorted()
                matchCandidateRepository.findAll().filter { it.quizSetId == oneToOneId } shouldHaveSize 2
                groupMatchRepository.findByQuizSetId(groupId) shouldHaveSize 1
                matchCandidateRepository.findAll().filter { it.quizSetId == openId } shouldHaveSize 0
            }

            "이미 후보가 있는 셋은 다시 처리하지 않는다 (멱등)" {
                val quizSetId = saveEndedQuizSetCompletedBy(MatchingType.ONE_TO_ONE, listOf("멱등A", "멱등B"))
                matchingBatchFacade.runScheduledMatching(batchAt) shouldBe listOf(quizSetId)

                matchingBatchFacade.runScheduledMatching(batchAt) shouldBe emptyList()
            }
        }
    },
)
