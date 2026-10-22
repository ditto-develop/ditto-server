package com.ditto.domain.memberreport.repository.querydsl

import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.quiz.entity.MatchingType
import java.time.LocalDate

interface MemberReportRepositoryCustom {

    /**
     * 그 주에 접수된 신고를 고른 사유 조합·처리 상태로 묶은 수. 회원 조건은 피신고자에게 건다.
     * matchingType 을 주면 피신고자가 그 주에 그 유형 퀴즈셋을 완주한 신고만 센다.
     * 신고에는 어느 매칭에서 나왔는지가 없어 피신고자가 푼 퀴즈셋 유형으로 대신한다.
     */
    fun countMemberReportsReceivedInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): List<MemberReportCount>
}
