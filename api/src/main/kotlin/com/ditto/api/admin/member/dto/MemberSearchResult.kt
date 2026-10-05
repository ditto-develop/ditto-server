package com.ditto.api.admin.member.dto

/** 닉네임 검색은 최대 limit명까지 담고, 더 맞는 회원이 있으면 isTruncated 가 켜진다. */
class MemberSearchResult(
    val members: List<MemberSummary>,
    val limit: Int,
    val isTruncated: Boolean,
)
