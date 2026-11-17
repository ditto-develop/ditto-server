package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.CandidateOrigin

/** 시크릿 매칭 라운드([candidateOrigin])마다 그 라운드에서 후보를 받은 회원 수. */
data class SecretCandidateOwnerCount(
    val candidateOrigin: CandidateOrigin,
    val count: Long,
)
