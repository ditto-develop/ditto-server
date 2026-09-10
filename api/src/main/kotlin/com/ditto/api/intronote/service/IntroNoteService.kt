package com.ditto.api.intronote.service

import com.ditto.api.intronote.dto.IntroNoteResponse
import com.ditto.api.intronote.dto.IntroNotesResponse
import com.ditto.api.match.MatchAccessChecker
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.member.repository.MemberBlockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

@Service
class IntroNoteService(
    private val introNoteRepository: IntroNoteRepository,
    private val matchAccessChecker: MatchAccessChecker,
    private val memberBlockRepository: MemberBlockRepository,
) {

    /** 질문 하나의 답변을 저장/수정(upsert)하고 전체 소개노트를 반환한다. */
    @Transactional
    fun saveAnswer(memberId: Long, questionCode: String, answer: String): IntroNotesResponse {
        val question = IntroQuestion.from(questionCode)
        val existing = introNoteRepository.findByMemberIdAndQuestion(memberId, question)
        if (existing != null) {
            existing.updateAnswer(answer)
        } else {
            introNoteRepository.save(IntroNote.create(memberId, question, answer))
        }
        return buildResponse(memberId)
    }

    /** 본인 소개노트 전체 조회. */
    @Transactional(readOnly = true)
    fun getMyIntroNotes(memberId: Long): IntroNotesResponse = buildResponse(memberId)

    /**
     * 타인 소개노트 조회. 관계에 따라 공개 범위가 다르다.
     * - 매칭 성사 또는 같은 그룹 채팅 참여: 전체 질문
     * - 이번 주 매칭 후보(성사 전): 미리보기 3문항만 ([buildPreviewResponse])
     * - 그 외: FORBIDDEN
     *
     * 성사 전 구간은 "대화 신청 여부를 정하는 화면"(피그마 3.2)이 쓰는 판단 근거라 열되,
     * 아직 아무 관계도 아닌 상대에게 10문항을 다 주지는 않는다.
     */
    @Transactional(readOnly = true)
    fun getIntroNotes(viewerId: Long, targetId: Long): IntroNotesResponse {
        if (viewerId == targetId) return buildResponse(targetId)
        if (matchAccessChecker.isMatched(viewerId, targetId)) return buildResponse(targetId)
        // 후보 행은 계산 시점의 스냅샷이라 그 뒤에 생긴 차단이 반영되지 않는다.
        // 성사 전은 이번에 새로 여는 구간이므로 차단이면 후보여도 막는다("차단한 사용자는 나의 프로필을 볼 수 없고", 피그마 6.2.2).
        if (matchAccessChecker.isMatchCandidate(viewerId, targetId) &&
            !memberBlockRepository.existsBetween(viewerId, targetId)
        ) {
            return buildPreviewResponse(viewerId, targetId)
        }

        throw WarnException(ErrorCode.FORBIDDEN)
    }

    private fun buildResponse(memberId: Long): IntroNotesResponse =
        toResponse(answersOf(memberId), IntroQuestion.entries)

    /**
     * 성사 전 후보에게 보여 줄 미리보기. 무작위 2문항 + 고정 [PREVIEW_FIXED_QUESTION](화면 마지막 칸)만 담는다.
     *
     * 무작위 선택은 (조회자, 대상자) 쌍으로 **결정적**이다 — 매 호출마다 다시 뽑으면
     * 새로고침할 때마다 화면이 바뀌고, 반복 호출로 전체 문항을 다 긁어갈 수 있다.
     *
     * 무작위 대상은 **작성된 답변**뿐이다(빈 카드를 뽑아 슬롯을 낭비하지 않는다).
     * 고정 문항은 미작성이어도 항상 포함해 FE 의 마지막 칸 계약을 지킨다.
     */
    private fun buildPreviewResponse(viewerId: Long, targetId: Long): IntroNotesResponse {
        val answerByQuestion = answersOf(targetId)
        val answered = IntroQuestion.entries.filter { question ->
            question != PREVIEW_FIXED_QUESTION && !answerByQuestion[question].isNullOrBlank()
        }
        val picked = answered
            .shuffled(Random(previewSeed(viewerId, targetId)))
            .take(PREVIEW_RANDOM_COUNT)
            .toSet()

        val questions = IntroQuestion.entries.filter { it in picked || it == PREVIEW_FIXED_QUESTION }
        return toResponse(answerByQuestion, questions)
    }

    private fun previewSeed(viewerId: Long, targetId: Long): Long = viewerId * PREVIEW_SEED_PRIME + targetId

    private fun answersOf(memberId: Long): Map<IntroQuestion, String> =
        introNoteRepository.findAllByMemberId(memberId)
            .associate { it.question to it.answer }

    /** 주어진 질문들을 고정 질문 순서대로 담아 응답을 만든다. 미작성 질문은 빈 문자열이다. */
    private fun toResponse(
        answerByQuestion: Map<IntroQuestion, String>,
        questions: List<IntroQuestion>,
    ): IntroNotesResponse {
        val answers = questions.map { question ->
            IntroNoteResponse(
                questionCode = question.code,
                question = question.text,
                answer = answerByQuestion[question] ?: "",
            )
        }
        val completedCount = answers.count { it.answer.isNotBlank() }
        return IntroNotesResponse(answers = answers, completedCount = completedCount)
    }

    companion object {
        /** 미리보기 마지막 칸에 고정으로 들어가는 질문 (피그마 3.2) */
        private val PREVIEW_FIXED_QUESTION = IntroQuestion.ONE_WORD

        /** 고정 질문 외에 미리보기에 넣는 무작위 문항 수 */
        private const val PREVIEW_RANDOM_COUNT = 2

        /** 조회자·대상자를 섞어 시드로 만드는 홀수 소수 — 쌍마다 다른 조합이 나오게 한다. */
        private const val PREVIEW_SEED_PRIME = 31L
    }
}
