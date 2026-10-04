package com.ditto.api.admin.dummy

import com.ditto.api.admin.dummy.cleanup.DummyCleanupSummary
import com.ditto.api.admin.dummy.cleanup.DummyDataCleaner
import com.ditto.api.admin.dummy.dto.DummyGenerateForm
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizAnswer
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.random.Random

/**
 * 어드민 편의 기능 — 특정 퀴즈셋을 랜덤하게 푼(COMPLETED) 더미 회원을 남/여 인원수만큼 생성한다.
 * 회원(member)·진행(quiz_progress)·답변(quiz_answer)만 만들고 매칭 후보(match_candidate)는
 * 만들지 않는다(어드민 '매칭 재생성'으로 분리). 더미는 닉네임 마커([DummyMarker])로 식별·정리한다.
 */
@Service
@Transactional
class AdminDummyService(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val memberRepository: MemberRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val dummyDataCleaner: DummyDataCleaner,
) {
    /** 더미를 생성하고 생성된 인원수를 반환한다. */
    fun generate(form: DummyGenerateForm): Int {
        validate(form)

        val quizSet = quizSetRepository.findById(form.quizSetId)
            .orElseThrow { WarnException(ErrorCode.NOT_FOUND) }

        val quizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id)
            .ifEmpty { throw WarnException(ErrorCode.BAD_REQUEST, "문항이 없는 퀴즈셋에는 더미를 생성할 수 없습니다.") }

        val choicesByQuizId = quizChoiceRepository
            .findByQuizIdInOrderByDisplayOrderAsc(quizzes.map { it.id })
            .groupBy { it.quizId }

        val context = SolveContext(quizSet.id, quizzes, choicesByQuizId)
        val genderCounts = listOf(Gender.MALE to form.maleCount, Gender.FEMALE to form.femaleCount)

        return genderCounts.sumOf { (gender, count) ->
            repeat(count) { createDummy(gender, form, context) }
            count
        }
    }

    /** 더미가 낀 채팅방·매칭은 같은 방·그룹의 실회원 쪽 행과 알림까지 함께 지운다(ADR 0038). */
    fun deleteAllDummies(): DummyCleanupSummary {
        val dummyIds = memberRepository.findByNicknameStartingWith(DummyMarker.NICKNAME_PREFIX).map { it.id }
        if (dummyIds.isEmpty()) return DummyCleanupSummary.NONE

        val summary = dummyDataCleaner.deleteDataOf(dummyIds)
        quizAnswerRepository.deleteByMemberIdIn(dummyIds)
        quizProgressRepository.deleteByMemberIdIn(dummyIds)
        matchCandidateRepository.deleteByOwnerOrOtherMemberIdIn(dummyIds)
        memberRepository.deleteAllByIdInBatch(dummyIds)
        return summary
    }

    /** 현재 더미 회원 수(현황 표시용). */
    @Transactional(readOnly = true)
    fun countDummies(): Long = memberRepository.countByNicknameStartingWith(DummyMarker.NICKNAME_PREFIX)

    private fun validate(form: DummyGenerateForm) {
        if (form.maleCount < 0 || form.femaleCount < 0) {
            throw WarnException(ErrorCode.BAD_REQUEST, "생성 인원수는 음수일 수 없습니다.")
        }
        if (form.maleCount + form.femaleCount == 0) {
            throw WarnException(ErrorCode.BAD_REQUEST, "생성할 인원수를 입력해주세요.")
        }
        if (form.minAge <= 0) {
            throw WarnException(ErrorCode.BAD_REQUEST, "나이는 1 이상이어야 합니다.")
        }
        if (form.minAge > form.maxAge) {
            throw WarnException(ErrorCode.BAD_REQUEST, "최소 나이가 최대 나이보다 클 수 없습니다.")
        }
    }

    private fun createDummy(gender: Gender, form: DummyGenerateForm, context: SolveContext) {
        val member = memberRepository.save(newDummyMember(gender, form))
        saveCompletedProgress(member.id, context)
        saveRandomAnswers(member.id, context)
    }

    private fun newDummyMember(gender: Gender, form: DummyGenerateForm): Member {
        val suffix = UUID.randomUUID().toString().take(8)
        val nickname = "${DummyMarker.NICKNAME_PREFIX}${gender.name.lowercase()}-$suffix"
        // 실제 가입과 동일하게 register() 로 활성화한다(ACTIVE 전이·joinedAt·필수 프로필을 도메인이 소유).
        // gender·age 가 null 이면 매칭 후보 풀에서 제외되므로 더미는 반드시 채운다.
        return Member(nickname = nickname, email = "$nickname@$EMAIL_DOMAIN").apply {
            register(
                name = null,
                nickname = null,
                phoneNumber = null,
                gender = gender,
                age = Random.nextInt(form.minAge, form.maxAge + 1),
                birthDate = null,
                email = null,
                interests = randomInterests(),
                location = Location.entries.random(),
                job = Job.entries.random(),
                caricature = randomCaricatureOf(gender),
            )
        }
    }

    // FE 가입 화면처럼 1~5개를 고른다.
    private fun randomInterests(): Set<Interest> =
        Interest.entries.shuffled().take(Random.nextInt(1, MAX_INTEREST_COUNT + 1)).toSet()

    // FE 가입 화면이 고르는 아바타 경로와 같은 형식이다. FE가 이 경로를 바꾸면 함께 고쳐야 한다.
    private fun randomCaricatureOf(gender: Gender): String {
        val genderInitial = when (gender) {
            Gender.MALE -> "m"
            Gender.FEMALE -> "f"
        }
        val avatarNumber = Random.nextInt(1, CARICATURE_COUNT_PER_GENDER + 1)
        return "$CARICATURE_PATH_PREFIX$genderInitial$avatarNumber.svg"
    }

    private fun saveCompletedProgress(memberId: Long, context: SolveContext) {
        val progress = QuizProgress.create(memberId, context.quizSetId, context.quizzes.size)
        // status·answeredCount 는 protected set 이라 recordAnswer 를 문항 수만큼 호출해야 COMPLETED 가 된다.
        repeat(context.quizzes.size) { progress.recordAnswer() }
        quizProgressRepository.save(progress)
    }

    private fun saveRandomAnswers(memberId: Long, context: SolveContext) {
        val answers = context.quizzes.map { quiz ->
            val choices = context.choicesByQuizId[quiz.id]
                ?: throw WarnException(ErrorCode.BAD_REQUEST, "선택지가 없는 문항이 있어 더미를 생성할 수 없습니다: quizId=${quiz.id}")
            QuizAnswer.create(memberId, quiz.id, choices.random().id)
        }
        quizAnswerRepository.saveAll(answers)
    }

    private class SolveContext(
        val quizSetId: Long,
        val quizzes: List<Quiz>,
        val choicesByQuizId: Map<Long, List<QuizChoice>>,
    )

    companion object {
        private const val EMAIL_DOMAIN = "dummy.local"
        private const val CARICATURE_PATH_PREFIX = "/onboarding/profileimg/avatar/"
        private const val CARICATURE_COUNT_PER_GENDER = 8
        private const val MAX_INTEREST_COUNT = 5
    }
}
