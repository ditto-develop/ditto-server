package com.ditto.domain.member.repository.querydsl

data class PushDeviceCoverage(
    val memberCount: Long,
    val memberWithPushDeviceCount: Long,
)
