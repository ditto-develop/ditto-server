package com.ditto.api.admin.member.dto

import com.ditto.api.admin.quiz.dto.ParticipantMatching
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizSet

/** [rows]는 퀴즈셋 전체를 최신 주부터 담고, 참여하지 않은 셋은 진행·매칭이 null 이다. */
class MemberQuizzesView(
    val member: MemberSummary,
    val rows: List<MemberQuizSetRow>,
) {
    val participatedCount: Int = rows.count { it.progress != null }
}

class MemberQuizSetRow(
    val quizSet: QuizSet,
    val progress: QuizProgress?,
    val matching: ParticipantMatching?,
)
