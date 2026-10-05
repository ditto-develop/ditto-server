package com.ditto.api.admin.dummy

import com.ditto.api.admin.dummy.cleanup.DummyCleanupSummary
import com.ditto.api.admin.dummy.cleanup.DummyDataCleaner
import com.ditto.api.admin.dummy.dto.DummyGenerateForm
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

/**
 * 어드민 편의 기능 — 특정 퀴즈셋을 랜덤하게 푼(COMPLETED) 더미 회원을 남/여 인원수만큼 생성한다.
 * 회원(member)·진행(quiz_progress)·답변(quiz_answer)만 만들고 매칭 후보(match_candidate)는
 * 만들지 않는다(어드민 '매칭 재생성'으로 분리). 더미는 닉네임 마커([DummyMarker])로 식별·정리한다.
 */
@Service
@Transactional
class AdminDummyService(
    private val memberRepository: MemberRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val dummyAnswerRecorder: DummyAnswerRecorder,
    private val dummyDataCleaner: DummyDataCleaner,
) {
    /** 더미를 생성하고 생성된 인원수를 반환한다. */
    fun generate(form: DummyGenerateForm): Int {
        validate(form)
        val questions = dummyAnswerRecorder.findQuestionsOf(form.quizSetId)
        val genderCounts = listOf(Gender.MALE to form.maleCount, Gender.FEMALE to form.femaleCount)

        return genderCounts.sumOf { (gender, count) ->
            repeat(count) { createRandomDummy(gender, form, questions) }
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
            throw WarnException(ErrorCode.BAD_REQUEST, "남자 수·여자 수는 0 이상이어야 합니다.")
        }
        if (form.maleCount + form.femaleCount == 0) {
            throw WarnException(ErrorCode.BAD_REQUEST, "남자 수나 여자 수를 1명 이상 넣어 주세요.")
        }
        if (form.minAge <= 0) {
            throw WarnException(ErrorCode.BAD_REQUEST, "나이는 1 이상이어야 합니다.")
        }
        if (form.minAge > form.maxAge) {
            throw WarnException(ErrorCode.BAD_REQUEST, "최소 나이가 최대 나이보다 클 수 없습니다.")
        }
    }

    private fun createRandomDummy(gender: Gender, form: DummyGenerateForm, questions: QuizQuestions) {
        val profile = DummyProfile(
            nickname = DummyMemberFactory.autoNickname(gender),
            gender = gender,
            age = Random.nextInt(form.minAge, form.maxAge + 1),
            interests = DummyMemberFactory.randomInterests(),
            location = DummyMemberFactory.randomLocation(),
            job = DummyMemberFactory.randomJob(),
            caricature = DummyMemberFactory.randomCaricatureOf(gender),
        )
        val member = memberRepository.save(DummyMemberFactory.create(profile))
        dummyAnswerRecorder.recordAnswers(member.id, questions, questions.pickAllRandomly())
    }
}
