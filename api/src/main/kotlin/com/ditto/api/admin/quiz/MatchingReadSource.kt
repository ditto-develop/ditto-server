package com.ditto.api.admin.quiz

import com.ditto.domain.member.entity.Member
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizSet

/** 참여 현황이 이미 읽은 퀴즈셋·진행 기록·회원. 매칭 정보를 읽을 때 같은 데이터를 다시 조회하지 않게 넘긴다. */
class MatchingReadSource(
    val quizSet: QuizSet,
    val progresses: List<QuizProgress>,
    val membersById: Map<Long, Member>,
) {
    val memberIds: List<Long> = progresses.map { it.memberId }
}
