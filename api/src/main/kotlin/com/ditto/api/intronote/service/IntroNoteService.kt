package com.ditto.api.intronote.service

import com.ditto.api.intronote.dto.IntroNoteResponse
import com.ditto.api.intronote.dto.IntroNotesResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IntroNoteService(
    private val introNoteRepository: IntroNoteRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
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

    /** 타인 소개노트 조회. 매칭 성사 또는 같은 그룹 채팅 참여자만 가능. */
    @Transactional(readOnly = true)
    fun getIntroNotes(viewerId: Long, targetId: Long): IntroNotesResponse {
        if (viewerId != targetId && !canView(viewerId, targetId)) {
            throw WarnException(ErrorCode.FORBIDDEN)
        }
        return buildResponse(targetId)
    }

    private fun canView(viewerId: Long, targetId: Long): Boolean {
        val matched = personalMatchRepository.existsByMemberId1AndMemberId2AndStatus(
            minOf(viewerId, targetId),
            maxOf(viewerId, targetId),
            PersonalMatchStatus.ACCEPTED,
        )
        if (matched) return true
        return groupMatchMemberRepository.existsSharedRoom(viewerId, targetId)
    }

    private fun buildResponse(memberId: Long): IntroNotesResponse {
        val answerByQuestion = introNoteRepository.findAllByMemberId(memberId)
            .associate { it.question to it.answer }

        val answers = IntroQuestion.entries.map { question ->
            IntroNoteResponse(
                questionCode = question.code,
                question = question.text,
                answer = answerByQuestion[question] ?: "",
            )
        }
        val completedCount = answers.count { it.answer.isNotBlank() }
        return IntroNotesResponse(answers = answers, completedCount = completedCount)
    }
}
