package com.ditto.api.notification

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.notice.AdminNoticeService
import com.ditto.api.notification.facade.SystemNoticeFacade
import com.ditto.api.notification.notifier.SystemNoticeNotifier
import com.ditto.domain.member.repository.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataAccessResourceFailureException

/** 사람이 결과를 보는 경로다. 대상 조회 실패는 삼키지 않고 이력도 남기지 않아야 한다. */
class SystemNoticeFacadeFailureTest : FreeSpec({
    val memberRepository = mockk<MemberRepository>()
    val adminNoticeService = mockk<AdminNoticeService>(relaxed = true)
    val systemNoticeNotifier = mockk<SystemNoticeNotifier>(relaxed = true)
    val facade = SystemNoticeFacade(memberRepository, adminNoticeService, systemNoticeNotifier)

    "회원 조회가 실패하면 예외가 올라가고 이력은 남지 않는다" {
        every { memberRepository.findAllIdsByStatus(any()) } throws DataAccessResourceFailureException("커넥션을 얻지 못했습니다")

        shouldThrow<DataAccessResourceFailureException> {
            facade.publish("공지", null, AdminPrincipal(1L, "관리자", "admin@ditto.pics"))
        }

        verify(exactly = 0) { adminNoticeService.record(any(), any(), any(), any()) }
        verify(exactly = 0) { systemNoticeNotifier.notifyPublished(any(), any()) }
    }
})
