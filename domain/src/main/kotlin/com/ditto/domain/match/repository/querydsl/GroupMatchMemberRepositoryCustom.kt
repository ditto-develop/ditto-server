package com.ditto.domain.match.repository.querydsl

interface GroupMatchMemberRepositoryCustom {

    fun existsByMemberIdAndQuizSetId(memberId: Long, quizSetId: Long): Boolean
}
