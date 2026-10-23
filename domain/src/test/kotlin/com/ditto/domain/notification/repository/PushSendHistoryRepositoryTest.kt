package com.ditto.domain.notification.repository

import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.entity.PushSendHistory
import com.ditto.domain.notification.entity.PushSkipReason
import com.ditto.domain.notification.repository.querydsl.PushSendHistoryCount
import com.ditto.domain.support.IntegrationTest
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class PushSendHistoryRepositoryTest(
    private val pushSendHistoryRepository: PushSendHistoryRepository,
    private val memberRepository: MemberRepository,
    private val dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun save(): PushSendHistory = pushSendHistoryRepository.save(
        PushSendHistory.skipped(NotificationFixture.create(id = 1L), PushSkipReason.NO_DEVICE),
    )

    "deleteCreatedBefore" - {
        "기준 시각 이전에 만들어진 이력을 지운다" {
            save()
            save()

            pushSendHistoryRepository.deleteCreatedBefore(LocalDateTime.now().plusDays(1)) shouldBe 2
            pushSendHistoryRepository.count() shouldBe 0
        }

        "기준 시각 이후의 이력은 남긴다" {
            save()

            pushSendHistoryRepository.deleteCreatedBefore(LocalDateTime.now().minusDays(1)) shouldBe 0
            pushSendHistoryRepository.count() shouldBe 1
        }
    }

    "countHistoriesInWeek" - {
        // created_at 이 저장 시각으로 채워져서 이번 주로 센다.
        val week = OperationWeek.containing(LocalDate.now()).startedOn

        fun saveMember(nickname: String, role: MemberRole = MemberRole.USER): Long = memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", role = role),
        ).id

        fun notificationFor(memberId: Long, type: NotificationType = NotificationType.CHAT_MESSAGE) =
            NotificationFixture.create(memberId = memberId, type = type, id = 1L)

        fun moveCreatedAt(history: PushSendHistory, createdAt: LocalDateTime) {
            dataSource.connection.use { connection ->
                val sql = "UPDATE push_send_history SET created_at = ? WHERE id = ?"
                connection.prepareStatement(sql).use { statement ->
                    statement.setObject(1, createdAt)
                    statement.setLong(2, history.id)
                    statement.executeUpdate()
                }
            }
        }

        "그 주 USER 이력만 유형·건너뛴 이유·실패 기기 수로 묶어 센다" {
            val user = saveMember("user")
            repeat(2) { pushSendHistoryRepository.save(PushSendHistory.sent(notificationFor(user), 1, emptyList())) }
            pushSendHistoryRepository.save(PushSendHistory.sent(notificationFor(user), 2, listOf("UNAVAILABLE")))
            pushSendHistoryRepository.save(
                PushSendHistory.skipped(notificationFor(user, NotificationType.MATCH_RESULT), PushSkipReason.NO_DEVICE),
            )
            val admin = saveMember("admin", MemberRole.ADMIN)
            pushSendHistoryRepository.save(PushSendHistory.sent(notificationFor(admin), 1, emptyList()))
            moveCreatedAt(
                pushSendHistoryRepository.save(PushSendHistory.sent(notificationFor(user), 1, emptyList())),
                week.minusWeeks(1).atStartOfDay(),
            )

            val counts = pushSendHistoryRepository.countHistoriesInWeek(week, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                PushSendHistoryCount(NotificationType.CHAT_MESSAGE, null, 0, 2L),
                PushSendHistoryCount(NotificationType.CHAT_MESSAGE, null, 1, 1L),
                PushSendHistoryCount(NotificationType.MATCH_RESULT, PushSkipReason.NO_DEVICE, 0, 1L),
            )
        }
    }
})
