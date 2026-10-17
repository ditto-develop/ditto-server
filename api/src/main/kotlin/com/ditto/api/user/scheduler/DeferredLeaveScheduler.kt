package com.ditto.api.user.scheduler

import com.ditto.api.user.service.DeferredLeaveService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 미뤄 둔 탈퇴를 매시 30분에 다시 시도한다. 정각에 도는 다른 크론과 겹치지 않게 30분에 둔다.
 * 채팅방 마감 같은 진행 종료가 한 시간 늦게 반영돼도 문제없다.
 * 한 명이 실패해도 나머지는 처리되게 회원마다 트랜잭션을 나눈다.
 */
@Component
class DeferredLeaveScheduler(
    private val deferredLeaveService: DeferredLeaveService,
) {

    @Scheduled(cron = "\${member.deferred-leave.scheduler.cron:0 30 * * * *}")
    fun leaveDeferredMembers() {
        val memberIds = deferredLeaveService.findDeferredMemberIds()
        if (memberIds.isEmpty()) return

        val leftCount = memberIds.count { memberId ->
            runCatching { deferredLeaveService.leaveIfNothingInProgress(memberId) }
                .onFailure { logger.error(it) { "미뤄 둔 탈퇴 처리 실패: memberId=$memberId" } }
                .getOrDefault(false)
        }
        logger.info { "미뤄 둔 탈퇴 ${memberIds.size}명 중 ${leftCount}명 탈퇴 처리" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
