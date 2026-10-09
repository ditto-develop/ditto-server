package com.ditto.api.user.service

import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 진행 중인 매칭·채팅 때문에 미뤄 둔 탈퇴를 진행이 끝난 뒤 처리한다. */
@Service
class DeferredLeaveService(
    private val memberRepository: MemberRepository,
    private val leaveProgressChecker: LeaveProgressChecker,
    private val memberLeaveProcessor: MemberLeaveProcessor,
    private val serverTimeProvider: ServerTimeProvider,
) {

    @Transactional(readOnly = true)
    fun findDeferredMemberIds(): List<Long> =
        memberRepository.findAllByDeferredLeaveReasonIsNotNull().map { it.id }

    @Transactional
    fun leaveIfNothingInProgress(memberId: Long): Boolean {
        // 잠근 뒤 다시 본다. 그 사이 재로그인으로 대기가 풀렸을 수 있다.
        val member = memberRepository.findWithLockById(memberId) ?: return false
        val reason = member.deferredLeaveReason ?: return false
        if (member.isLeft()) return false
        if (leaveProgressChecker.hasInProgress(member.id, serverTimeProvider.now())) return false

        memberLeaveProcessor.leave(member, reason = reason)
        return true
    }
}
