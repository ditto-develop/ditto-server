package com.ditto.domain.quiz.repository.querydsl

interface QuizAnswerRepositoryCustom {
    fun deleteByMemberIdAndQuizIds(memberId: Long, quizIds: List<Long>)

    /** 답변 없는 문항은 키가 없다. quiz_answer 는 FK 없이 quiz_id 를 참조해 삭제를 DB 가 막아주지 않는다. */
    fun countAnswersPerQuiz(quizIds: List<Long>): Map<Long, Long>
}
