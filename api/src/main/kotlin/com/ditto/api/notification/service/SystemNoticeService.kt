package com.ditto.api.notification.service

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.SystemNotice
import com.ditto.domain.notification.repository.SystemNoticeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 시스템 공지 이력. 발송 자체는 SystemNoticeFacade 가 이 트랜잭션 밖에서 한다. */
@Service
@Transactional
class SystemNoticeService(
    private val systemNoticeRepository: SystemNoticeRepository,
) {

    /** 문구 길이 제한은 알림과 같다. 공지 문구가 그대로 알림 문구가 된다. */
    fun record(title: String, body: String?, author: AdminPrincipal): SystemNotice {
        if (title.isBlank()) {
            throw WarnException(ErrorCode.BAD_REQUEST, "제목을 입력하세요.")
        }
        if (title.length > Notification.TITLE_MAX_LENGTH) {
            throw WarnException(ErrorCode.BAD_REQUEST, "제목은 ${Notification.TITLE_MAX_LENGTH}자 이하여야 합니다.")
        }
        if (body != null && body.length > Notification.BODY_MAX_LENGTH) {
            throw WarnException(ErrorCode.BAD_REQUEST, "본문은 ${Notification.BODY_MAX_LENGTH}자 이하여야 합니다.")
        }
        return systemNoticeRepository.save(
            SystemNotice.create(
                title = title.trim(),
                body = body?.trim()?.takeIf { it.isNotEmpty() },
                authorMemberId = author.memberId,
                authorName = author.name,
                authorEmail = author.email,
            ),
        )
    }

    fun recordRecipientCount(noticeId: Long, count: Int): SystemNotice {
        val notice = systemNoticeRepository.findById(noticeId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        notice.recordRecipientCount(count)
        return notice
    }

    @Transactional(readOnly = true)
    fun history(): List<SystemNotice> = systemNoticeRepository.findAllByOrderByIdDesc()
}
