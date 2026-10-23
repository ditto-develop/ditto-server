package com.ditto.domain.notification.repository

import com.ditto.domain.notification.entity.PushSendHistory
import com.ditto.domain.notification.repository.querydsl.PushSendHistoryRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface PushSendHistoryRepository : JpaRepository<PushSendHistory, Long>, PushSendHistoryRepositoryCustom
