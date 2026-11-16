package com.ditto.api.notification

import com.ditto.api.notification.notifier.MatchResultNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.api.system.featureflag.FeatureFlagAdminService
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

private const val QUIZ_SET = 1L
private const val MEMBER_A = 1L
private const val MEMBER_B = 2L
private const val MEMBER_C = 3L

// 시크릿 매칭 플래그가 꺼진 테스트라 시각은 문구에 영향이 없다.
private val NOW = LocalDateTime.of(2026, 4, 16, 0, 1)

class MatchResultNotifierTest(
    private val matchResultNotifier: MatchResultNotifier,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    private val featureFlags: FeatureFlags,
    private val featureFlagAdminService: FeatureFlagAdminService,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    beforeTest { featureFlags.evict() }
    afterTest { featureFlags.evict() }

    fun saveCandidatePair(quizSetId: Long = QUIZ_SET) {
        // 후보는 (A→B), (B→A) 두 방향 행으로 저장된다.
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = MEMBER_A, otherMemberId = MEMBER_B, quizSetId = quizSetId),
        )
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = MEMBER_B, otherMemberId = MEMBER_A, quizSetId = quizSetId),
        )
    }

    fun saveActiveMember(nickname: String) =
        memberRepository.save(MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = MemberStatus.ACTIVE))

    fun saveCompletedProgress(memberId: Long, quizSetId: Long) {
        quizProgressRepository.save(
            QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
                .apply { recordAnswer() },
        )
    }

    "후보가 생긴 회원에게 알린다" - {
        "쌍의 양쪽 모두 알림을 받는다" {
            saveCandidatePair()

            matchResultNotifier.notifyFor(listOf(QUIZ_SET), NOW) shouldBe 2

            val notifications = notificationRepository.findAll()
            notifications.map { it.memberId }.toSet() shouldBe setOf(MEMBER_A, MEMBER_B)
            notifications.first().type shouldBe NotificationType.MATCH_RESULT
            notifications.first().title shouldBe "같은 답을 한 사람을 찾았어요"
            // 대상은 퀴즈셋이다 — "주마다 한 번"을 판정하는 기준이다.
            notifications.first().targetId shouldBe QUIZ_SET
        }

        "다시 불러도 중복으로 남지 않는다 — 어드민이 후보를 재생성할 수 있다" {
            saveCandidatePair()
            matchResultNotifier.notifyFor(listOf(QUIZ_SET), NOW)

            matchResultNotifier.notifyFor(listOf(QUIZ_SET), NOW) shouldBe 0

            notificationRepository.count() shouldBe 2
        }

        "후보가 없는 퀴즈셋에는 알리지 않는다 — 빈 화면으로 보내지 않는다" {
            matchResultNotifier.notifyFor(listOf(QUIZ_SET), NOW) shouldBe 0

            notificationRepository.count() shouldBe 0
        }

        "대상 퀴즈셋이 없으면 아무 것도 하지 않는다" {
            matchResultNotifier.notifyFor(emptyList(), NOW) shouldBe 0
        }
    }

    "퀴즈를 끝냈지만 후보가 없는 회원에게는 노매칭을 알린다" - {
        "매칭 풀에 들었으나 후보가 없는 회원만 받는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val (a, b, c) = listOf("a", "b", "c").map { saveActiveMember(it) }
            listOf(a, b, c).forEach { saveCompletedProgress(it.id, quizSet.id) }
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = a.id, otherMemberId = b.id, quizSetId = quizSet.id))
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = b.id, otherMemberId = a.id, quizSetId = quizSet.id))

            matchResultNotifier.notifyFor(listOf(quizSet.id), NOW) shouldBe 3

            val noMatch = notificationRepository.findAll().single { it.type == NotificationType.NO_MATCH }
            noMatch.memberId shouldBe c.id
            noMatch.title shouldBe "이번 주는 답이 닿지 않았어요"
            noMatch.body shouldBe "다음 주에 새로운 질문으로 다시 찾아볼게요."
            noMatch.targetId shouldBe quizSet.id
        }

        "퀴즈를 끝내지 않은 회원은 받지 않는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            quizProgressRepository.save(QuizProgressFixture.create(memberId = MEMBER_C, quizSetId = quizSet.id))

            matchResultNotifier.notifyFor(listOf(quizSet.id), NOW) shouldBe 0

            notificationRepository.count() shouldBe 0
        }

        "매칭 풀에서 빠진 회원(탈퇴 등)은 받지 않는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val left = memberRepository.save(MemberFixture.create(status = MemberStatus.LEFT))
            saveCompletedProgress(left.id, quizSet.id)

            matchResultNotifier.notifyFor(listOf(quizSet.id), NOW) shouldBe 0

            notificationRepository.count() shouldBe 0
        }

        // 그룹에는 제외 정책이 없어 풀 계산만으로는 정지·탈퇴 회원이 남는다
        "그룹 퀴즈셋에서도 탈퇴 회원은 받지 않는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create(matchingType = MatchingType.GROUP))
            val left = memberRepository.save(MemberFixture.create(status = MemberStatus.LEFT))
            saveCompletedProgress(left.id, quizSet.id)

            matchResultNotifier.notifyFor(listOf(quizSet.id), NOW) shouldBe 0

            notificationRepository.count() shouldBe 0
        }

        "후보 재생성으로 후보가 사라진 회원에게 노매칭을 덧붙이지 않는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val (a, b) = listOf("a", "b").map { saveActiveMember(it) }
            listOf(a, b).forEach { saveCompletedProgress(it.id, quizSet.id) }
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = a.id, otherMemberId = b.id, quizSetId = quizSet.id))
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = b.id, otherMemberId = a.id, quizSetId = quizSet.id))
            matchResultNotifier.notifyFor(listOf(quizSet.id), NOW) shouldBe 2

            matchCandidateRepository.deleteAll()
            matchResultNotifier.notifyFor(listOf(quizSet.id), NOW) shouldBe 0

            notificationRepository.findAll().map { it.type }.toSet() shouldBe setOf(NotificationType.MATCH_RESULT)
        }
    }

    "시크릿 매칭이 켜져 있을 때 노매칭 문구" - {
        // 2026-11-16(월) 시작 주의 목요일 정규 배치 직후
        val monday = LocalDate.of(2026, 11, 16)
        val thursday = LocalDateTime.of(2026, 11, 19, 0, 1)

        fun weekQuizSet(matchingType: MatchingType) = quizSetRepository.save(
            QuizSetFixture.create(
                startDate = monday.atStartOfDay(),
                endDate = monday.plusDays(2).atTime(23, 59, 59),
                matchingType = matchingType,
            ),
        )

        fun noMatchBodyAfterNotifying(quizSetId: Long): String {
            val member = saveActiveMember("c")
            saveCompletedProgress(member.id, quizSetId)
            matchResultNotifier.notifyFor(listOf(quizSetId), thursday)
            return notificationRepository.findAll().single { it.type == NotificationType.NO_MATCH }.body.orEmpty()
        }

        beforeEach { featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = true, adminMemberId = 1L) }

        "그 주 1:1 셋의 노매칭은 금요일 라운드를 안내한다" {
            val oneToOne = weekQuizSet(MatchingType.ONE_TO_ONE)

            noMatchBodyAfterNotifying(oneToOne.id) shouldBe "금요일 저녁 8시에 한 번 더 찾아볼게요."
            notificationRepository.findAll().single().title shouldBe "이번엔 인연이 닿지 않았어요"
        }

        "그룹 셋의 노매칭은 다음 주 안내 문구를 쓴다" {
            weekQuizSet(MatchingType.ONE_TO_ONE)
            val group = weekQuizSet(MatchingType.GROUP)

            noMatchBodyAfterNotifying(group.id) shouldBe "다음 주에 새로운 질문으로 다시 찾아볼게요."
        }

        "지난 주 1:1 셋의 노매칭이 늦게 나가면 다음 주 안내 문구를 쓴다" {
            val lastWeek = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = monday.minusWeeks(1).atStartOfDay(),
                    endDate = monday.minusWeeks(1).plusDays(2).atTime(23, 59, 59),
                    matchingType = MatchingType.ONE_TO_ONE,
                ),
            )

            noMatchBodyAfterNotifying(lastWeek.id) shouldBe "다음 주에 새로운 질문으로 다시 찾아볼게요."
        }

        "놓친 정규 배치를 시크릿 라운드가 메우면 시크릿 후보를 받은 회원에게 노매칭을 보내지 않는다" {
            val oneToOne = weekQuizSet(MatchingType.ONE_TO_ONE)
            val member = saveActiveMember("c")
            saveCompletedProgress(member.id, oneToOne.id)
            matchCandidateRepository.save(
                MatchCandidateFixture.create(
                    ownerMemberId = member.id,
                    otherMemberId = 99L,
                    quizSetId = oneToOne.id,
                    candidateOrigin = CandidateOrigin.SECRET_FIRST,
                ),
            )

            matchResultNotifier.notifyFor(listOf(oneToOne.id), thursday) shouldBe 0

            notificationRepository.count() shouldBe 0
        }
    }
})
