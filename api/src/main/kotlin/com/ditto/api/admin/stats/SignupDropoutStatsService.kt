package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.SignupDropoutStatsData
import com.ditto.domain.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 가입 중인 회원은 성별·생년월일이 없어 회원 조건을 걸지 않는다. 가입 시작 시각이 실제 시각이라 이탈 기준도 실제 시각으로 잰다.
@Service
class SignupDropoutStatsService(
    private val memberRepository: MemberRepository,
) {

    @Transactional(readOnly = true)
    fun findStats(): SignupDropoutStatsData = SignupDropoutStatsData(
        dropoutHours = DROPOUT_HOURS,
        signupDropouts = memberRepository.findSignupDropoutsStartedBefore(LocalDateTime.now().minusHours(DROPOUT_HOURS)),
        joinedCountsBySocialProvider = memberRepository.countJoinedMembersBySocialProvider(),
    )

    companion object {
        // 막 가입을 시작한 회원은 아직 가입 중일 수 있어 하루가 지나야 이탈로 센다.
        const val DROPOUT_HOURS = 24L
    }
}
