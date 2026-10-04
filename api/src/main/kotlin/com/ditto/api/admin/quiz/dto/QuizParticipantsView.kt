package com.ditto.api.admin.quiz.dto

import com.ditto.api.admin.dummy.DummyMarker
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.GenderPreference
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet

class QuizParticipantsView(
    val quizSet: QuizSet,
    val quizzes: List<Quiz>,
    val participants: List<QuizParticipant>,
) {
    val completedCount: Int = countByStatus(QuizProgressStatus.COMPLETED)
    val inProgressCount: Int = countByStatus(QuizProgressStatus.IN_PROGRESS)
    val notStartedCount: Int = countByStatus(QuizProgressStatus.NOT_STARTED)
    val dummyCount: Int = participants.count { it.isDummy }
    val realMemberCount: Int = participants.size - dummyCount

    private fun countByStatus(status: QuizProgressStatus): Int = participants.count { it.progressStatus == status }
}

/** 이름·전화번호·이메일·생년월일 같은 신원 정보는 싣지 않는다. 회원 행이 지워졌으면 프로필이 모두 비어 있다. */
class QuizParticipant(
    val memberId: Long,
    val nickname: String?,
    val memberStatus: MemberStatus?,
    val progressStatus: QuizProgressStatus,
    val answeredCount: Int,
    val totalCount: Int,
    val preferredGender: GenderPreference,
    val gender: Gender?,
    val age: Int?,
    val location: Location?,
    val job: Job?,
    val interests: Set<Interest>,
    val caricatureFileName: String?,
    val answerContents: List<String?>,
) {
    val isDeletedMember: Boolean = nickname == null
    val isDummy: Boolean = nickname != null && DummyMarker.isDummy(nickname)

    companion object {
        /** [answerContents]는 퀴즈셋 문항 순서대로 고른 선택지 내용이고, 안 푼 문항은 null 이다. */
        fun of(progress: QuizProgress, member: Member?, answerContents: List<String?>): QuizParticipant =
            QuizParticipant(
                memberId = progress.memberId,
                nickname = member?.nickname,
                memberStatus = member?.status,
                progressStatus = progress.status,
                answeredCount = progress.answeredCount,
                totalCount = progress.totalCount,
                preferredGender = progress.preferredGender,
                gender = member?.gender,
                age = member?.age,
                location = member?.location,
                job = member?.job,
                interests = member?.interests.orEmpty(),
                caricatureFileName = member?.caricature?.substringAfterLast('/')?.substringBeforeLast(".svg"),
                answerContents = answerContents,
            )
    }
}
