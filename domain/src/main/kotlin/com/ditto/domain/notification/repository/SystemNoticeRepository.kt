package com.ditto.domain.notification.repository

import com.ditto.domain.notification.entity.SystemNotice
import org.springframework.data.jpa.repository.JpaRepository

interface SystemNoticeRepository : JpaRepository<SystemNotice, Long> {

    /** 어드민 이력 화면용. 공지는 많지 않아 페이징하지 않는다. */
    fun findAllByOrderByIdDesc(): List<SystemNotice>
}
