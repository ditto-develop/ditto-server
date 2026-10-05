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
    val matching: QuizSetMatching,
) {
    val participantCount: Int = participants.size
    val completedCount: Int = countByStatus(QuizProgressStatus.COMPLETED)
    val inProgressCount: Int = countByStatus(QuizProgressStatus.IN_PROGRESS)
    val notStartedCount: Int = countByStatus(QuizProgressStatus.NOT_STARTED)
    val realMemberCount: Int = countByKind(QuizParticipantKind.REAL)
    val dummyCount: Int = countByKind(QuizParticipantKind.DUMMY)
    val deletedMemberCount: Int = countByKind(QuizParticipantKind.DELETED)

    private fun countByStatus(status: QuizProgressStatus): Int = participants.count { it.progressStatus == status }

    private fun countByKind(kind: QuizParticipantKind): Int = participants.count { it.kind == kind }
}

/** 화면 정렬 순서이기도 하다. 테스터 계정이 더미 사이에 묻히지 않게 실회원을 먼저 둔다. */
enum class QuizParticipantKind {
    REAL,
    DUMMY,

    /** 탈퇴 보존 기간이 지나 회원 행이 지워졌다. 탈퇴(LEFT)했지만 행이 남은 회원은 [REAL]이다. */
    DELETED,
}

/** 이름·전화번호·이메일·생년월일 같은 신원 정보는 싣지 않는다. */
class QuizParticipant(
    val memberId: Long,
    val kind: QuizParticipantKind,
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
    companion object {
        /** [answerContents]는 퀴즈셋 문항 순서대로 고른 선택지 내용이고, 안 푼 문항은 null 이다. */
        fun of(progress: QuizProgress, member: Member?, answerContents: List<String?>): QuizParticipant =
            QuizParticipant(
                memberId = progress.memberId,
                kind = kindOf(member),
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

        private fun kindOf(member: Member?): QuizParticipantKind = when {
            member == null -> QuizParticipantKind.DELETED
            DummyMarker.isDummy(member.nickname) -> QuizParticipantKind.DUMMY
            else -> QuizParticipantKind.REAL
        }
    }
}
