package com.ditto.api.admin.member.dto

import com.ditto.api.admin.dummy.DummyMarker
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus

/** 닉네임·회원 ID 검색 결과 한 줄. 이메일 같은 신원 정보는 싣지 않는다. */
class MemberSearchResult(
    val memberId: Long,
    val nickname: String,
    val status: MemberStatus,
    val isDummy: Boolean,
) {
    companion object {
        fun of(member: Member): MemberSearchResult =
            MemberSearchResult(member.id, member.nickname, member.status, DummyMarker.isDummy(member.nickname))
    }
}
