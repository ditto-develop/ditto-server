package com.ditto.domain.member.repository.querydsl

data class ActivePushDeviceCount(
    val activeMemberCount: Long,
    val activeMemberWithPushDeviceCount: Long,
)
