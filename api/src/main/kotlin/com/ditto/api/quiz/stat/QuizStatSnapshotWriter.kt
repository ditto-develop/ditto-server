package com.ditto.api.quiz.stat

import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.QuizChoiceStat
import com.ditto.domain.quiz.entity.QuizMemberStat
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSetStat
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizChoiceStatRepository
import com.ditto.domain.quiz.repository.QuizMemberStatRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetStatRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 퀴즈셋 하나의 통계 스냅샷을 계산해 저장한다. 머리 행·선택지·회원 행을 **자기 트랜잭션 하나에** 담는다 —
 * 머리 행(`quiz_set_stat`)이 있으면 나머지도 있다는 것이 읽는 쪽의 전제다.
 *
 * `REQUIRES_NEW`인 이유: 조회 API 가 읽기 트랜잭션 안에서 지연 생성을 부른다. 동시에 두 요청이 만들면
 * 머리 행 유니크가 한쪽을 막고, 진 쪽은 자기 트랜잭션만 버린 채 바깥에서 다시 읽는다([QuizStatSnapshotService]).
 */
@Component
class QuizStatSnapshotWriter(
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizSetStatRepository: QuizSetStatRepository,
    private val quizChoiceStatRepository: QuizChoiceStatRepository,
    private val quizMemberStatRepository: QuizMemberStatRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun write(quizSetId: Long) {
        val quizIds = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId).map { it.id }
        val choiceIdsByQuizId = quizChoiceRepository.findByQuizIdInOrderByDisplayOrderAsc(quizIds)
            .groupBy({ it.quizId }, { it.id })
        // 선택지가 하나도 없는 문항도 문항 수에는 들어간다(겹친 사람 기준이 문항 수로 정해진다).
        val orderedChoices = quizIds.associateWith { choiceIdsByQuizId[it].orEmpty() }

        val result = QuizStatCalculator.calculate(orderedChoices, answersOfParticipants(quizSetId, quizIds))

        quizSetStatRepository.saveAndFlush(
            QuizSetStat.of(quizSetId, result.participantCount, result.overlapThreshold),
        )
        quizChoiceStatRepository.saveAll(
            result.choices.map { QuizChoiceStat.of(quizSetId, it.quizId, it.choiceId, it.selectCount, it.percent) },
        )
        quizMemberStatRepository.saveAll(
            result.members.map { QuizMemberStat.of(quizSetId, it.memberId, it.minorityCount, it.overlapCount) },
        )
    }

    /** 완주자(어드민 제외)의 `memberId → (quizId → choiceId)`. 중간에 멈춘 사람은 기록·통계 모두에서 빠진다. */
    private fun answersOfParticipants(quizSetId: Long, quizIds: List<Long>): Map<Long, Map<Long, Long>> {
        val completedIds = quizProgressRepository.findByQuizSetIdAndStatus(quizSetId, QuizProgressStatus.COMPLETED)
            .map { it.memberId }
        if (completedIds.isEmpty() || quizIds.isEmpty()) return emptyMap()

        val participantIds = memberRepository.findAllById(completedIds).filterNot { it.isAdmin() }.map { it.id }
        val answersByMember = quizAnswerRepository.findByMemberIdInAndQuizIdIn(participantIds, quizIds)
            .groupBy { it.memberId }
        return participantIds.associateWith { id -> answersByMember[id].orEmpty().associate { it.quizId to it.choiceId } }
    }
}
