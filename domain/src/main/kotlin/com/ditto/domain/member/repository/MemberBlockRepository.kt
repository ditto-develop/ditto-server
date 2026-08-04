package com.ditto.domain.member.repository

import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.repository.querydsl.MemberBlockRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface MemberBlockRepository : JpaRepository<MemberBlock, Long>, MemberBlockRepositoryCustom {

    /** 내 차단 목록 — 화면이 최신순으로 노출한다. */
    fun findAllByBlockerIdOrderByCreatedAtDesc(blockerId: Long): List<MemberBlock>

    fun findByBlockerIdAndBlockedMemberId(blockerId: Long, blockedMemberId: Long): MemberBlock?

    fun existsByBlockerIdAndBlockedMemberId(blockerId: Long, blockedMemberId: Long): Boolean
}
