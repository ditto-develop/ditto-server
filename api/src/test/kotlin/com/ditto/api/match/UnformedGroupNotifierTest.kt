package com.ditto.api.match

import com.ditto.api.match.service.UnformedGroupNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * 인원 미달로 성사되지 못한 그룹의 수락자에게 취소를 알린다(QA BUG-071).
 *
 * 마감은 그 주 **금요일 00:00** 이고, 그룹에는 시각이 없어 퀴즈셋의 주 시작일로 판정한다.
 */
class UnformedGroupNotifierTest(
    private val unformedGroupNotifier: UnformedGroupNotifier,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        // 2026-06-01(월) 시작 주. 마감은 2026-06-05(금) 00:00.
        val monday = LocalDate.of(2026, 6, 1)
        val afterDeadline = LocalDateTime.of(2026, 6, 5, 0, 30)
        val beforeDeadline = LocalDateTime.of(2026, 6, 4, 23, 30)

        fun quizSetId(weekStartedOn: LocalDate = monday): Long = quizSetRepository.save(
            QuizSetFixture.create(
                startDate = weekStartedOn.atStartOfDay(),
                endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
            ),
        ).id

        /** 수락자 [acceptedMemberIds] 를 가진 미성사 그룹. 임계값(3) 미만이라 활성화되지 않는다. */
        fun unformedGroup(acceptedMemberIds: List<Long>, weekStartedOn: LocalDate = monday): GroupMatch {
            val group = groupMatchRepository.save(
                GroupMatchFixture.create(quizSetId = quizSetId(weekStartedOn)),
            )
            acceptedMemberIds.forEach { memberId ->
                val member = GroupMatchMember.candidate(roomId = group.id, memberId = memberId)
                member.accept()
                groupMatchMemberRepository.save(member)
            }
            return group
        }

        fun notifications(memberId: Long) = notificationRepository
            .findAll()
            .filter { it.memberId == memberId && it.type == NotificationType.GROUP_NOT_FORMED }

        "인원 미달 안내" - {
            "마감이 지난 미성사 그룹의 수락자에게 알린다" {
                unformedGroup(listOf(1L, 2L))

                unformedGroupNotifier.notifyUnformed(afterDeadline) shouldBe 2

                notifications(1L).size shouldBe 1
                notifications(2L).size shouldBe 1
            }

            "마감 전에는 알리지 않는다 — 아직 수락이 더 들어올 수 있다" {
                unformedGroup(listOf(1L))

                unformedGroupNotifier.notifyUnformed(beforeDeadline) shouldBe 0

                notifications(1L).size shouldBe 0
            }

            // 수락한 적 없는 사람에게 "취소됐다"고 알리면 맥락 없는 알림이 된다.
            "수락자가 없으면 아무에게도 알리지 않는다" {
                groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId()))

                unformedGroupNotifier.notifyUnformed(afterDeadline) shouldBe 0
            }

            // 스케줄러가 1분마다 돌므로 중복 방지가 없으면 같은 알림이 계속 쌓인다.
            "여러 번 돌아도 한 번만 알린다" {
                unformedGroup(listOf(1L))

                unformedGroupNotifier.notifyUnformed(afterDeadline)
                unformedGroupNotifier.notifyUnformed(afterDeadline)

                notifications(1L).size shouldBe 1
            }

            // 알림 행은 30일 뒤 purge 된다. 스캔이 그보다 오래 거슬러 올라가면 존재 검사가 다시
            // 통과해 한참 전에 끝난 그룹의 안내와 푸시가 다시 나간다.
            "오래된 주차의 미성사 그룹은 대상이 아니다" {
                unformedGroup(listOf(1L), weekStartedOn = monday.minusDays(21))

                unformedGroupNotifier.notifyUnformed(afterDeadline) shouldBe 0

                notifications(1L).size shouldBe 0
            }

            "지난주 미성사 그룹은 아직 대상이다 — 스케줄러가 멈췄다 돌아도 따라잡는다" {
                unformedGroup(listOf(1L), weekStartedOn = monday.minusDays(7))

                unformedGroupNotifier.notifyUnformed(afterDeadline) shouldBe 1

                notifications(1L).size shouldBe 1
            }

            "성사된 그룹은 대상이 아니다" {
                val formed = groupMatchRepository.save(
                    GroupMatchFixture.create(quizSetId = quizSetId(), acceptedCount = 3),
                )
                listOf(1L, 2L, 3L).forEach { memberId ->
                    val member = GroupMatchMember.candidate(roomId = formed.id, memberId = memberId)
                    member.accept()
                    groupMatchMemberRepository.save(member)
                }

                unformedGroupNotifier.notifyUnformed(afterDeadline) shouldBe 0
            }
        }
    },
)
