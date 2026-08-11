package com.ditto.domain.notification.repository

import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.NotificationCategory
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private const val ME = 1L
private const val OTHER = 2L

/** 조회 창의 하한. 저장 직후의 행은 모두 포함된다. */
private val LONG_AGO = LocalDateTime.of(2020, 1, 1, 0, 0)

class NotificationRepositoryTest(
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun save(
        memberId: Long = ME,
        type: NotificationType = NotificationType.MATCH_RESULT,
        targetId: Long? = 1L,
        title: String = "제목",
    ) = notificationRepository.save(
        NotificationFixture.create(memberId = memberId, type = type, targetId = targetId, title = title),
    )

    "findByMemberIdWithCursor — 내 알림 커서 페이징(최신순)" - {
        "given: 내 알림 5건이 있을 때" - {
            "when: 커서 없이 size=2 로 조회하면" - {
                "then: 최신순으로 2건을 반환한다" {
                    (1..5).forEach { save(title = "n$it") }

                    val result = notificationRepository
                        .findByMemberIdWithCursor(ME, category = null, cursor = null, size = 2, from = LONG_AGO)

                    result.map { it.title } shouldBe listOf("n5", "n4")
                }
            }

            "when: 커서를 주면" - {
                "then: 그 id 미만(더 과거)만 최신순으로 반환한다" {
                    val saved = (1..5).map { save(title = "n$it") }

                    val result = notificationRepository
                        .findByMemberIdWithCursor(ME, category = null, cursor = saved[2].id, size = 10, from = LONG_AGO)

                    result.map { it.id } shouldBe listOf(saved[1].id, saved[0].id)
                }
            }
        }

        "given: 남의 알림이 섞여 있을 때" - {
            "when: 내 알림을 조회하면" - {
                "then: 내 것만 반환한다" {
                    save(memberId = ME, title = "내 알림")
                    save(memberId = OTHER, title = "남의 알림")

                    val result = notificationRepository
                        .findByMemberIdWithCursor(ME, category = null, cursor = null, size = 10, from = LONG_AGO)

                    result.map { it.title } shouldBe listOf("내 알림")
                }
            }
        }

        "given: 카테고리가 다른 알림이 섞여 있을 때" - {
            "when: 카테고리로 필터하면" - {
                "then: 그 카테고리의 유형만 반환한다" {
                    save(type = NotificationType.MATCH_RESULT, title = "매칭")
                    save(type = NotificationType.CHAT_MESSAGE, title = "새 메시지")
                    save(type = NotificationType.CHAT_ENDING_SOON, title = "종료 임박")

                    val result = notificationRepository.findByMemberIdWithCursor(
                        ME,
                        category = NotificationCategory.CHAT,
                        cursor = null,
                        size = 10,
                        from = LONG_AGO,
                    )

                    result.map { it.title } shouldBe listOf("종료 임박", "새 메시지")
                }
            }
        }

        // 보관 기간(30일)을 넘긴 알림은 화면에서 사라져야 한다.
        "given: 조회 창의 하한이 저장 시각보다 뒤일 때" - {
            "when: 조회하면" - {
                "then: 아무 것도 반환하지 않는다" {
                    save()

                    val result = notificationRepository.findByMemberIdWithCursor(
                        ME,
                        category = null,
                        cursor = null,
                        size = 10,
                        from = LocalDateTime.now().plusDays(1),
                    )

                    result.size shouldBe 0
                }
            }
        }
    }

    "markAllRead — 안읽은 알림을 한 번에 읽음으로" - {
        "안읽은 내 알림만 읽음이 된다" {
            val mine = save(memberId = ME)
            val alreadyRead = save(memberId = ME)
            alreadyRead.markRead(LocalDateTime.now())
            notificationRepository.save(alreadyRead)
            val others = save(memberId = OTHER)

            val readCount = notificationRepository.markAllRead(ME, LocalDateTime.now())

            readCount shouldBe 1
            notificationRepository.findByIdAndMemberId(mine.id, ME)!!.isRead shouldBe true
            notificationRepository.findByIdAndMemberId(others.id, OTHER)!!.isRead shouldBe false
        }

        "이미 다 읽었으면 0을 반환한다 — 멱등하다" {
            save(memberId = ME)
            notificationRepository.markAllRead(ME, LocalDateTime.now())

            notificationRepository.markAllRead(ME, LocalDateTime.now()) shouldBe 0
        }
    }

    "deleteUnread — 새 메시지 알림 접기" - {
        "같은 (회원·유형·대상)의 안읽은 알림만 지운다" {
            val unread = save(type = NotificationType.CHAT_MESSAGE, targetId = 10L, title = "안읽음")
            val read = save(type = NotificationType.CHAT_MESSAGE, targetId = 10L, title = "읽음")
            read.markRead(LocalDateTime.now())
            notificationRepository.save(read)
            val otherRoom = save(type = NotificationType.CHAT_MESSAGE, targetId = 11L, title = "다른 방")

            val deleted = notificationRepository.deleteUnread(ME, NotificationType.CHAT_MESSAGE, targetId = 10L)

            deleted shouldBe 1
            notificationRepository.findById(unread.id).isPresent shouldBe false
            notificationRepository.findById(read.id).isPresent shouldBe true
            notificationRepository.findById(otherRoom.id).isPresent shouldBe true
        }
    }

    "deleteCreatedBefore — 보관 기간 경과분 정리" - {
        "기준 시각 이전에 만들어진 알림을 지운다" {
            save()
            save()

            val deleted = notificationRepository.deleteCreatedBefore(LocalDateTime.now().plusDays(1))

            deleted shouldBe 2
            notificationRepository.count() shouldBe 0
        }

        "기준 시각 이후의 알림은 남긴다" {
            save()

            notificationRepository.deleteCreatedBefore(LocalDateTime.now().minusDays(1)) shouldBe 0
        }
    }

    "미읽음 수·중복 검사" - {
        "미읽음 수는 창 안의 안읽은 내 알림만 센다" {
            save(memberId = ME)
            save(memberId = ME)
            save(memberId = OTHER)

            notificationRepository
                .countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(ME, LONG_AGO) shouldBe 2
            notificationRepository
                .countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(ME, LocalDateTime.now().plusDays(1)) shouldBe 0
        }

        "같은 (회원·유형·대상)의 알림이 이미 있는지 답한다 — 한 번만 알리기의 근거" {
            save(type = NotificationType.REVIEW_REQUEST, targetId = 7L)

            notificationRepository
                .existsByMemberIdAndTypeAndTargetId(ME, NotificationType.REVIEW_REQUEST, 7L) shouldBe true
            notificationRepository
                .existsByMemberIdAndTypeAndTargetId(ME, NotificationType.REVIEW_REQUEST, 8L) shouldBe false
            notificationRepository
                .existsByMemberIdAndTypeAndTargetId(OTHER, NotificationType.REVIEW_REQUEST, 7L) shouldBe false
        }

        "탈퇴 완전 삭제용으로 회원의 알림을 모두 지운다" {
            save(memberId = ME)
            save(memberId = ME)
            save(memberId = OTHER)

            notificationRepository.deleteAllByMemberId(ME)

            notificationRepository.count() shouldBe 1
        }
    }
})
