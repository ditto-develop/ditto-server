package com.ditto.api.admin.stats

import com.ditto.api.admin.dummy.DummyMarker
import com.ditto.api.admin.stats.dto.SignupDropoutStatsData
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.MemberFilter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 가입 중인 회원은 성별·생년월일이 없어 회원 조건을 걸지 않는다. 더미는 가입 화면을 거치지 않아 뺀다.
@Service
class SignupDropoutStatsService(
    private val memberRepository: MemberRepository,
) {

    @Transactional(readOnly = true)
    fun findStats(): SignupDropoutStatsData {
        // 가입 시작 시각(created_at)이 시간 조정과 무관한 실제 시각이라 기준도 실제 시각으로 잰다.
        val now = LocalDateTime.now()
        return SignupDropoutStatsData(
            dropoutHours = DROPOUT_HOURS,
            realToday = now.toLocalDate(),
            signupDropouts = memberRepository.findSignupDropoutsStartedBefore(now.minusHours(DROPOUT_HOURS)),
            joinedCountsBySocialProvider =
                memberRepository.countJoinedMembersBySocialProvider(DummyMarker.NICKNAME_PREFIX),
            minIntroNoteCountForQuiz = MIN_INTRO_NOTE_COUNT_FOR_QUIZ,
            quizBlockedMembers = memberRepository.findActiveMembersWithIntroNotesUnder(
                MIN_INTRO_NOTE_COUNT_FOR_QUIZ,
                DummyMarker.NICKNAME_PREFIX,
            ),
            activeStatusMemberCount = memberRepository
                .countActiveMembersWithPushDevice(MemberFilter.NONE, DummyMarker.NICKNAME_PREFIX)
                .memberCount,
        )
    }

    companion object {
        // 막 가입을 시작한 회원은 아직 가입 중일 수 있어 24시간이 지나야 이탈로 센다.
        private const val DROPOUT_HOURS = 24L

        // 소개노트가 이보다 적으면 앱 홈이 퀴즈 시작을 막는다(FE MIN_INTRO_NOTE_ANSWERS). 서버는 막지 않는다.
        private const val MIN_INTRO_NOTE_COUNT_FOR_QUIZ = 3L
    }
}
