package com.ditto.api.admin.member.dto

class MemberSearchResult(
    val members: List<MemberSummary>,
    val isTruncated: Boolean,
) {
    companion object {
        val EMPTY = MemberSearchResult(emptyList(), isTruncated = false)
    }
}
