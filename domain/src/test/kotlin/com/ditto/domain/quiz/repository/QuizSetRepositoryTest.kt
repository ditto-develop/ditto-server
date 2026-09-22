package com.ditto.domain.quiz.repository

import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class QuizSetRepositoryTest(
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val groupMatchRepository: GroupMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val now = LocalDateTime.of(2026, 4, 10, 12, 0)

    "findCurrentWeekActive" - {
        "현재 시간이 startDate~endDate 범위 안이고 활성이면 조회된다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1), isActive = true),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 1
        }

        "비활성 퀴즈 세트는 조회되지 않는다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1), isActive = false),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 0
        }

        "현재 시간이 startDate 이전이면 조회되지 않는다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.plusDays(1), endDate = now.plusDays(7), isActive = true),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 0
        }

        "현재 시간이 endDate 이후이면 조회되지 않는다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(7), endDate = now.minusDays(1), isActive = true),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 0
        }

        "경계값 - startDate와 같은 시간이면 조회된다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now, endDate = now.plusDays(7), isActive = true),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 1
        }

        "경계값 - endDate와 같은 시간이면 조회된다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(7), endDate = now, isActive = true),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 1
        }

        "여러 활성 퀴즈 세트가 있으면 모두 조회된다" {
            quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = now.minusDays(1), endDate = now.plusDays(1),
                    matchingType = MatchingType.ONE_TO_ONE, category = "성격",
                ),
            )
            quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = now.minusDays(1), endDate = now.plusDays(1),
                    matchingType = MatchingType.GROUP, category = "취미",
                ),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 2
        }
    }

    "findCompletedQuizSetInWeek" - {
        // now(2026-04-10, 금)가 속한 운영 주는 4/6(월) 시작이다.
        val thisWeek = LocalDate.of(2026, 4, 6)
        val lastWeek = LocalDate.of(2026, 3, 30)

        fun saveCompletedProgress(memberId: Long, quizSetId: Long) {
            val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
            progress.recordAnswer() // NOT_STARTED -> COMPLETED
            quizProgressRepository.save(progress)
        }

        "그 주에 완주한 해당 타입 퀴즈셋을 반환한다" {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = thisWeek.atStartOfDay(),
                    endDate = thisWeek.plusDays(2).atTime(23, 59, 59),
                ),
            )
            saveCompletedProgress(memberId = 1L, quizSetId = quizSet.id)

            val result = quizSetRepository.findCompletedQuizSetInWeek(1L, MatchingType.ONE_TO_ONE, thisWeek)

            result?.id shouldBe quizSet.id
        }

        "비활성 퀴즈셋은 제외된다" {
            val inactive = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = thisWeek.atStartOfDay(),
                    endDate = thisWeek.plusDays(2).atTime(23, 59, 59),
                    isActive = false,
                ),
            )
            saveCompletedProgress(memberId = 1L, quizSetId = inactive.id)

            val result = quizSetRepository.findCompletedQuizSetInWeek(1L, MatchingType.ONE_TO_ONE, thisWeek)

            result shouldBe null
        }

        "같은 주차에 활성 셋이 둘이면 나중에 만든 것을 반환한다" {
            // 기간이 월~수로 고정돼 endDate 가 같으므로 id 가 순서를 가른다.
            listOf("먼저 만든 셋", "나중에 만든 셋").map { title ->
                val quizSet = quizSetRepository.save(
                    QuizSetFixture.create(
                        title = title,
                        startDate = thisWeek.atStartOfDay(),
                        endDate = thisWeek.plusDays(2).atTime(23, 59, 59),
                    ),
                )
                saveCompletedProgress(memberId = 1L, quizSetId = quizSet.id)
                quizSet
            }

            val result = quizSetRepository.findCompletedQuizSetInWeek(1L, MatchingType.ONE_TO_ONE, thisWeek)

            result?.title shouldBe "나중에 만든 셋"
        }

        "지난 주에 완주한 퀴즈셋은 제외된다" {
            val previous = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = lastWeek.atStartOfDay(),
                    endDate = lastWeek.plusDays(2).atTime(23, 59, 59),
                ),
            )
            saveCompletedProgress(memberId = 1L, quizSetId = previous.id)

            val result = quizSetRepository.findCompletedQuizSetInWeek(1L, MatchingType.ONE_TO_ONE, thisWeek)

            result shouldBe null
        }

        "지난 주 것과 이번 주 것이 모두 있으면 이번 주 것을 반환한다" {
            val previous = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = lastWeek.atStartOfDay(),
                    endDate = lastWeek.plusDays(2).atTime(23, 59, 59),
                ),
            )
            val current = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = thisWeek.atStartOfDay(),
                    endDate = thisWeek.plusDays(2).atTime(23, 59, 59),
                ),
            )
            saveCompletedProgress(memberId = 1L, quizSetId = previous.id)
            saveCompletedProgress(memberId = 1L, quizSetId = current.id)

            val result = quizSetRepository.findCompletedQuizSetInWeek(1L, MatchingType.ONE_TO_ONE, thisWeek)

            result?.id shouldBe current.id
        }

        "완료(COMPLETED)하지 않은 진행 기록만 있으면 제외된다" {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = thisWeek.atStartOfDay(),
                    endDate = thisWeek.plusDays(2).atTime(23, 59, 59),
                ),
            )
            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 5),
            )

            val result = quizSetRepository.findCompletedQuizSetInWeek(1L, MatchingType.ONE_TO_ONE, thisWeek)

            result shouldBe null
        }

        "같은 주라도 요청한 매칭 타입과 다른 퀴즈셋은 제외된다" {
            val groupSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = thisWeek.atStartOfDay(),
                    endDate = thisWeek.plusDays(2).atTime(23, 59, 59),
                    matchingType = MatchingType.GROUP,
                ),
            )
            saveCompletedProgress(memberId = 1L, quizSetId = groupSet.id)

            val result = quizSetRepository.findCompletedQuizSetInWeek(1L, MatchingType.ONE_TO_ONE, thisWeek)

            result shouldBe null
        }

        "다른 회원의 완료 기록은 제외된다" {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = thisWeek.atStartOfDay(),
                    endDate = thisWeek.plusDays(2).atTime(23, 59, 59),
                ),
            )
            saveCompletedProgress(memberId = 2L, quizSetId = quizSet.id)

            val result = quizSetRepository.findCompletedQuizSetInWeek(1L, MatchingType.ONE_TO_ONE, thisWeek)

            result shouldBe null
        }

        "완주한 퀴즈셋이 없으면 null 을 반환한다" {
            val result = quizSetRepository.findCompletedQuizSetInWeek(1L, MatchingType.ONE_TO_ONE, thisWeek)

            result shouldBe null
        }
    }

    "findEndedQuizSetsWithoutCandidates" - {
        val endedAfter = now.minusDays(14)

        "마감됐고 후보가 하나도 없으면 배치 대상이다" {
            quizSetRepository.save(QuizSetFixture.create(endDate = now.minusDays(1)))

            quizSetRepository.findEndedQuizSetsWithoutCandidates(endedAfter, now).size shouldBe 1
        }

        "아직 마감 전이면 배치 대상이 아니다" {
            quizSetRepository.save(QuizSetFixture.create(endDate = now.plusDays(1)))

            quizSetRepository.findEndedQuizSetsWithoutCandidates(endedAfter, now).size shouldBe 0
        }

        "하한보다 먼저 마감된 셋은 후보가 없어도 대상이 아니다" {
            quizSetRepository.save(QuizSetFixture.create(endDate = endedAfter.minusDays(1)))

            quizSetRepository.findEndedQuizSetsWithoutCandidates(endedAfter, now).size shouldBe 0
        }

        "1:1 후보(match_candidate)가 이미 있으면 제외된다" {
            val quizSetId = quizSetRepository.save(
                QuizSetFixture.create(endDate = now.minusDays(1), matchingType = MatchingType.ONE_TO_ONE),
            ).id
            matchCandidateRepository.save(MatchCandidateFixture.create(quizSetId = quizSetId))

            quizSetRepository.findEndedQuizSetsWithoutCandidates(endedAfter, now).size shouldBe 0
        }

        "그룹 후보(group_match)가 이미 있으면 제외된다" {
            // 그룹은 후보를 group_match 에 담으므로 match_candidate 만 보면 매주 다시 계산돼 후보 ID 가 갈린다
            val quizSetId = quizSetRepository.save(
                QuizSetFixture.create(endDate = now.minusDays(1), matchingType = MatchingType.GROUP),
            ).id
            groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))

            quizSetRepository.findEndedQuizSetsWithoutCandidates(endedAfter, now).size shouldBe 0
        }
    }
})
