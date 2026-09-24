package com.ditto.domain.notification.repository

import com.ditto.domain.notification.SystemNoticeFixture
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class SystemNoticeRepositoryTest(
    private val systemNoticeRepository: SystemNoticeRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "이력은 최근 것부터 나온다" {
        val first = systemNoticeRepository.save(SystemNoticeFixture.create(title = "첫 공지"))
        val second = systemNoticeRepository.save(SystemNoticeFixture.create(title = "둘째 공지"))

        systemNoticeRepository.findAllByOrderByIdDesc().map { it.id } shouldBe listOf(second.id, first.id)
    }
})
