package com.ditto.api.admin.member.dto

import com.ditto.api.admin.dummy.DummyMarker
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus

/** 회원 검색 결과·회원별 퀴즈 현황 머리글. 이메일 같은 신원 정보는 싣지 않는다. */
class MemberSummary(
    val memberId: Long,
    val nickname: String,
    val status: MemberStatus,
    val isDummy: Boolean,
) {
    companion object {
        fun of(member: Member): MemberSummary =
            MemberSummary(member.id, member.nickname, member.status, DummyMarker.isDummy(member.nickname))
    }
}
