package com.ditto.domain.notification.repository

import com.ditto.domain.notification.entity.PushSendHistory
import com.ditto.domain.notification.repository.querydsl.PushSendHistoryRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface PushSendHistoryRepository : JpaRepository<PushSendHistory, Long>, PushSendHistoryRepositoryCustom {

    fun findByMemberIdIn(memberIds: Collection<Long>): List<PushSendHistory>

    /** 탈퇴 회원을 완전히 지울 때 그 회원의 이력도 지운다. 지운 행 수를 돌려준다. */
    @Modifying
    @Query("DELETE FROM PushSendHistory h WHERE h.memberId = :memberId")
    fun deleteAllByMemberId(memberId: Long): Int
}
