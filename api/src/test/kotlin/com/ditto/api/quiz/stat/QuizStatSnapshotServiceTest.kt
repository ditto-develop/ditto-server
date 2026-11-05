package com.ditto.api.quiz.stat

import com.ditto.api.support.IntegrationTest
import com.ditto.api.support.QuizSeeder
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizChoiceStatRepository
import com.ditto.domain.quiz.repository.QuizMemberStatRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.quiz.repository.QuizSetStatRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class QuizStatSnapshotServiceTest(
    private val quizStatSnapshotService: QuizStatSnapshotService,
    private val memberRepository: MemberRepository,
    private val quizSetStatRepository: QuizSetStatRepository,
    private val quizChoiceStatRepository: QuizChoiceStatRepository,
    private val quizMemberStatRepository: QuizMemberStatRepository,
    quizSetRepository: QuizSetRepository,
    quizRepository: QuizRepository,
    quizChoiceRepository: QuizChoiceRepository,
    quizAnswerRepository: QuizAnswerRepository,
    quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val seeder = QuizSeeder(quizSetRepository, quizRepository, quizChoiceRepository, quizAnswerRepository, quizProgressRepository)
    var seq = 0

    fun member(role: MemberRole = MemberRole.USER) =
        memberRepository.save(MemberFixture.create(nickname = "회원${seq++}", status = MemberStatus.ACTIVE, role = role)).id

    "어드민을 포함한 완주자로 스냅샷을 만들고 중간에 멈춘 사람은 뺀다" {
        val set = seeder.quizSet(questions = 3)
        val a = member()
        val b = member()
        val admin = member(MemberRole.ADMIN)
        val stopped = member()
        seeder.answer(a, set, listOf(1, 1, 1))
        seeder.answer(b, set, listOf(1, 2, 1))
        seeder.answer(admin, set, listOf(2, 2, 1))
        seeder.answer(stopped, set, listOf(2))

        val snapshot = quizStatSnapshotService.getOrCreate(set.quizSetId)

        snapshot.participantCount shouldBe 3
        snapshot.overlapThreshold shouldBe 2
        snapshot.percentOf(set.choice(1, 1)) shouldBe 67
        snapshot.percentOf(set.choice(2, 1)) shouldBe 33
        quizMemberStatRepository.findByQuizSetIdAndMemberId(set.quizSetId, a)!!.overlapCount shouldBe 1
        quizMemberStatRepository.findByQuizSetIdAndMemberId(set.quizSetId, b)!!.overlapCount shouldBe 2
        quizMemberStatRepository.findByQuizSetIdAndMemberId(set.quizSetId, admin)!!.overlapCount shouldBe 1
        quizMemberStatRepository.findByQuizSetIdAndMemberId(set.quizSetId, stopped).shouldBeNull()
    }

    "이미 있으면 다시 계산하지 않는다 — 마감 뒤 들어온 답은 숫자를 바꾸지 않는다" {
        val set = seeder.quizSet(questions = 1)
        seeder.answer(member(), set, listOf(1))
        quizStatSnapshotService.getOrCreate(set.quizSetId)

        seeder.answer(member(), set, listOf(2))
        val again = quizStatSnapshotService.getOrCreate(set.quizSetId)

        again.participantCount shouldBe 1
        quizChoiceStatRepository.findByQuizSetId(set.quizSetId).size shouldBe 2
    }

    "배치는 최근 마감된 셋 중 스냅샷이 없는 셋만 만든다" {
        val ended = seeder.quizSet()
        seeder.answer(member(), ended, listOf(1, 1, 1))
        val notYetEnded = seeder.quizSet(weekStartedOn = QuizSeeder.CLOSED_WEEK.plusWeeks(1))

        val created = quizStatSnapshotService.snapshotEnded(LocalDateTime.of(2026, 10, 29, 0, 0))

        created shouldBe 1
        quizSetStatRepository.findByQuizSetId(ended.quizSetId).shouldNotBeNull()
        quizSetStatRepository.findByQuizSetId(notYetEnded.quizSetId).shouldBeNull()
        quizStatSnapshotService.snapshotEnded(LocalDateTime.of(2026, 10, 29, 0, 0)) shouldBe 0
    }

    "소수 의견은 참여자 수와 상관없이 선택 수 × 5 ≤ 참여자 수일 때다" {
        val set = seeder.quizSet(questions = 1)
        repeat(4) { seeder.answer(member(), set, listOf(1)) }
        seeder.answer(member(), set, listOf(2))

        val snapshot = quizStatSnapshotService.getOrCreate(set.quizSetId)

        snapshot.isMinority(set.choice(1, 2)) shouldBe true
        snapshot.isMinority(set.choice(1, 1)) shouldBe false
    }
})
