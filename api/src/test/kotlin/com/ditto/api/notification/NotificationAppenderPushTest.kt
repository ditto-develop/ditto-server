package com.ditto.api.notification

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.push.PushNotifier
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.notification.service.NotificationWriter
import com.ditto.domain.notification.NotificationFixture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/** 행이 생긴 알림만 푸시가 나간다 — 적재와 푸시의 연결을 본다. */
class NotificationAppenderPushTest : FreeSpec({

    // mock 호출 기록이 테스트 간 섞이지 않게 테스트마다 새로 만든다.
    lateinit var notificationWriter: NotificationWriter
    lateinit var pushNotifier: PushNotifier
    lateinit var notificationAppender: NotificationAppender
    beforeTest {
        notificationWriter = mockk()
        pushNotifier = mockk(relaxed = true)
        notificationAppender = NotificationAppender(notificationWriter, pushNotifier)
    }

    "행이 생기면 푸시가 나간다" {
        val saved = NotificationFixture.create(memberId = 1L, id = 10L)
        every { notificationWriter.write(1L, any(), any()) } returns saved

        notificationAppender.append(1L, NotificationMessages.matchResult(), targetId = 7L) shouldBe true

        verify(exactly = 1) { pushNotifier.pushAll(listOf(saved)) }
    }

    "이미 알린 사건(행 없음)이면 푸시도 없다" {
        every { notificationWriter.write(1L, any(), any()) } returns null

        notificationAppender.append(1L, NotificationMessages.matchResult(), targetId = 7L) shouldBe false

        verify(exactly = 0) { pushNotifier.pushAll(any()) }
    }

    "적재가 실패하면 false 를 돌려주고 푸시도 없다" {
        every { notificationWriter.write(1L, any(), any()) } throws RuntimeException("db down")

        notificationAppender.append(1L, NotificationMessages.matchResult(), targetId = 7L) shouldBe false

        verify(exactly = 0) { pushNotifier.pushAll(any()) }
    }

    "여러 수신자면 행이 생긴 알림만 모아 한 번에 넘긴다" {
        val savedFirst = NotificationFixture.create(memberId = 1L, id = 11L)
        val savedThird = NotificationFixture.create(memberId = 3L, id = 12L)
        every { notificationWriter.write(1L, any(), any()) } returns savedFirst
        every { notificationWriter.write(2L, any(), any()) } returns null // 이미 알린 사건
        every { notificationWriter.write(3L, any(), any()) } returns savedThird

        val appendedCount = notificationAppender.appendAll(listOf(1L, 2L, 3L), NotificationMessages.matchResult(), targetId = 7L)

        appendedCount shouldBe 2
        verify(exactly = 1) { pushNotifier.pushAll(listOf(savedFirst, savedThird)) }
    }
})
