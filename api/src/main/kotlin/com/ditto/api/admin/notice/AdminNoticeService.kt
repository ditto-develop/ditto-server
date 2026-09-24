package com.ditto.api.admin.notice

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.notification.entity.SystemNotice
import com.ditto.domain.notification.entity.SystemNoticeAuthor
import com.ditto.domain.notification.repository.SystemNoticeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 시스템 공지 이력. 문구 검증은 SystemNotice 가, 발송은 SystemNoticeFacade 가 이 트랜잭션 밖에서 한다. */
@Service
@Transactional
class AdminNoticeService(
    private val systemNoticeRepository: SystemNoticeRepository,
) {

    /** 발송 시작 시 대상 수와 함께 남긴다. 수신 수는 아직 없다(발송 중). */
    fun record(title: String, body: String?, author: AdminPrincipal, targetCount: Int): SystemNotice =
        systemNoticeRepository.save(
            SystemNotice.create(
                title = title,
                body = body,
                author = SystemNoticeAuthor(memberId = author.memberId, name = author.name, email = author.email),
                targetCount = targetCount,
            ),
        )

    fun recordRecipientCount(noticeId: Long, count: Int): SystemNotice {
        val notice = systemNoticeRepository.findById(noticeId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        notice.recordRecipientCount(count)
        return notice
    }

    @Transactional(readOnly = true)
    fun history(): List<SystemNotice> = systemNoticeRepository.findAllByOrderByIdDesc()
}
