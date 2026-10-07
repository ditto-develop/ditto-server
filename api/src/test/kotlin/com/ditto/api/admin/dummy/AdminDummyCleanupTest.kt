package com.ditto.api.admin.dummy

import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.ChatVoteFixture
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.chat.repository.ChatVoteChoiceRepository
import com.ditto.domain.chat.repository.ChatVoteOptionRepository
import com.ditto.domain.chat.repository.ChatVoteRepository
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.notification.MemberDeviceFixture
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import com.ditto.domain.sanction.SanctionFixture
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.repository.SanctionRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

class AdminDummyCleanupTest(
    private val adminDummyService: AdminDummyService,
    private val memberRepository: MemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatVoteRepository: ChatVoteRepository,
    private val chatVoteOptionRepository: ChatVoteOptionRepository,
    private val chatVoteChoiceRepository: ChatVoteChoiceRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val rematchRepository: RematchRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val memberReportRepository: MemberReportRepository,
    private val sanctionRepository: SanctionRepository,
    private val memberBlockRepository: MemberBlockRepository,
    private val memberDeviceRepository: MemberDeviceRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMember(nickname: String): Member =
        memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = MemberStatus.ACTIVE),
        )

    fun saveRoom(room: ChatRoom, members: List<Member>): ChatRoom {
        val saved = chatRoomRepository.save(room)
        chatRoomMemberRepository.saveAll(members.map { ChatRoomMemberFixture.create(saved.id, it.id) })
        members.forEach { chatMessageRepository.save(ChatMessageFixture.create(roomId = saved.id, senderId = it.id)) }
        return saved
    }

    fun saveGroup(members: List<Member>): GroupMatch {
        val group = groupMatchRepository.save(GroupMatch.candidate(quizSetId = 1L, score = 80.0))
        groupMatchMemberRepository.saveAll(members.map { GroupMatchMember.candidate(group.id, it.id) })
        return group
    }

    fun notify(member: Member, type: NotificationType, targetId: Long) =
        notificationRepository.save(NotificationFixture.create(memberId = member.id, type = type, targetId = targetId))

    "더미가 낀 채팅방" - {
        "방을 통째로 지우고 실회원의 메시지와 그 방을 가리키는 실회원 알림도 지운다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val qaRoom = saveRoom(ChatRoomFixture.group(sourceId = 1L), listOf(tester, dummy))
            val realRoom = saveRoom(ChatRoomFixture.personal(sourceId = 2L), listOf(tester, saveMember("실회원")))
            val vote = chatVoteRepository.save(ChatVoteFixture.open(roomId = qaRoom.id, createdBy = dummy.id))
            val option = chatVoteOptionRepository.save(ChatVoteFixture.place(voteId = vote.id, createdBy = dummy.id))
            chatVoteChoiceRepository.save(ChatVoteFixture.choice(vote.id, option.id, memberId = tester.id))
            notify(tester, NotificationType.CHAT_MESSAGE, qaRoom.id)
            val keptNotifications = listOf(
                notify(tester, NotificationType.CHAT_MESSAGE, realRoom.id),
                notify(tester, NotificationType.QUIZ_OPENED, qaRoom.id),
            )

            val summary = adminDummyService.deleteAllDummies()

            summary.roomCount shouldBe 1
            chatRoomRepository.findAll().map { it.id } shouldBe listOf(realRoom.id)
            chatRoomMemberRepository.findByRoomId(qaRoom.id).size shouldBe 0
            chatMessageRepository.findByRoomIdIn(listOf(qaRoom.id)).size shouldBe 0
            chatMessageRepository.findByRoomIdIn(listOf(realRoom.id)).size shouldBe 2
            chatVoteRepository.count() shouldBe 0
            chatVoteOptionRepository.count() shouldBe 0
            chatVoteChoiceRepository.count() shouldBe 0
            notificationRepository.findAll().map { it.id } shouldBe keptNotifications.map { it.id }
        }

        "그 방에서 열린 평가와 재매칭, 그것을 가리키는 알림을 지운다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val members = listOf(tester, dummy, saveMember("실회원"))
            val group = saveGroup(members)
            val qaRoom = saveRoom(ChatRoomFixture.group(sourceId = group.id), members)
            val review = memberReviewRepository.save(
                MemberReviewFixture.create(tester.id, matchType = ChatRoomType.GROUP, chatRoomId = qaRoom.id),
            )
            reviewAnswerRepository.save(ReviewAnswerFixture.pending(review.id, reviewedMemberId = dummy.id))
            val rematch = rematchRepository.save(
                RematchFixture.create(
                    sourceGroupMatchId = group.id,
                    sourceChatRoomId = qaRoom.id,
                    memberIdA = tester.id,
                    memberIdB = members[2].id,
                ),
            )
            notify(tester, NotificationType.REVIEW_REQUEST, qaRoom.id)
            notify(tester, NotificationType.REMATCH_REQUESTED, rematch.id)

            adminDummyService.deleteAllDummies()

            memberReviewRepository.count() shouldBe 0
            reviewAnswerRepository.count() shouldBe 0
            rematchRepository.count() shouldBe 0
            notificationRepository.count() shouldBe 0
        }
    }

    "더미가 낀 매칭" - {
        "1:1 신청과 그룹을 통째로 지우고 실회원끼리의 매칭은 남긴다" {
            val tester = saveMember("테스터")
            val other = saveMember("실회원")
            val dummy = saveMember("dummy-female-aaaa")
            val qaMatch = personalMatchRepository.save(PersonalMatchFixture.create(tester.id, receiverId = dummy.id))
            val realMatch = personalMatchRepository.save(PersonalMatchFixture.create(tester.id, receiverId = other.id))
            val qaGroup = saveGroup(listOf(tester, other, dummy))
            val realGroup = saveGroup(listOf(tester, other, saveMember("실회원2")))
            notify(tester, NotificationType.MATCH_ACCEPTED, qaMatch.id)
            notify(tester, NotificationType.GROUP_NOT_FORMED, qaGroup.id)

            val summary = adminDummyService.deleteAllDummies()

            summary.matchCount shouldBe 2
            personalMatchRepository.findAll().map { it.id } shouldBe listOf(realMatch.id)
            groupMatchRepository.findAll().map { it.id } shouldBe listOf(realGroup.id)
            groupMatchMemberRepository.findByRoomId(qaGroup.id).size shouldBe 0
            groupMatchMemberRepository.findByRoomId(realGroup.id).size shouldBe 3
            notificationRepository.count() shouldBe 0
        }
    }

    "더미가 거절해 방에 없는 그룹" - {
        "실회원끼리 연 그룹 방과 거기서 나온 재매칭 방도 원본과 함께 지운다" {
            val tester = saveMember("테스터")
            val others = listOf(saveMember("실회원1"), saveMember("실회원2"))
            val group = saveGroup(others + tester + saveMember("dummy-female-aaaa"))
            val groupRoom = saveRoom(ChatRoomFixture.group(sourceId = group.id), others + tester)
            val rematch = rematchRepository.save(
                RematchFixture.create(sourceGroupMatchId = group.id, memberIdA = tester.id, memberIdB = others[0].id),
            )
            val rematchRoom = saveRoom(ChatRoomFixture.rematch(sourceId = rematch.id), listOf(tester, others[0]))
            notify(tester, NotificationType.GROUP_FORMED, groupRoom.id)
            notify(tester, NotificationType.REMATCH_MATCHED, rematchRoom.id)

            val summary = adminDummyService.deleteAllDummies()

            summary.roomCount shouldBe 2
            chatRoomRepository.count() shouldBe 0
            chatMessageRepository.count() shouldBe 0
            groupMatchRepository.count() shouldBe 0
            rematchRepository.count() shouldBe 0
            notificationRepository.count() shouldBe 0
        }
    }

    "더미를 상대로 한 행동과 더미 본인 데이터" - {
        "신고·제재·차단과 더미의 기기·알림을 지운다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val report = memberReportRepository.save(MemberReportFixture.create(tester.id, reportedMemberId = dummy.id))
            val sanction = sanctionRepository.save(SanctionFixture.create(dummy.id, memberReportId = report.id))
            memberBlockRepository.save(MemberBlock.create(blockerId = tester.id, blockedMemberId = dummy.id))
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = dummy.id))
            notify(tester, NotificationType.REPORT_ACTIONED, report.id)
            notify(dummy, NotificationType.SANCTION_IMPOSED, sanction.id)

            adminDummyService.deleteAllDummies()

            memberReportRepository.count() shouldBe 0
            sanctionRepository.count() shouldBe 0
            memberBlockRepository.count() shouldBe 0
            memberDeviceRepository.count() shouldBe 0
            notificationRepository.count() shouldBe 0
            memberRepository.findAll().map { it.id } shouldBe listOf(tester.id)
        }
    }

    "더미가 실회원을 신고해 생긴 제재" - {
        fun banByDummyReport(tester: Member, dummy: Member) {
            val report = memberReportRepository.save(MemberReportFixture.create(dummy.id, reportedMemberId = tester.id))
            sanctionRepository.save(
                SanctionFixture.create(
                    tester.id,
                    origin = SanctionOrigin.REPORTED,
                    level = SanctionLevel.PERMANENT_BAN,
                    startsAt = LocalDateTime.now().minusHours(1),
                    memberReportId = report.id,
                ),
            )
            memberRepository.save(tester.apply { ban() })
        }

        "제재를 지우면서 테스트 계정을 정상으로 되돌린다" {
            val tester = saveMember("테스터")
            banByDummyReport(tester, saveMember("dummy-female-aaaa"))

            adminDummyService.deleteAllDummies()

            sanctionRepository.count() shouldBe 0
            memberRepository.findByIdOrNull(tester.id).shouldNotBeNull().status shouldBe MemberStatus.ACTIVE
        }

        "더미와 무관한 직접 제재가 남아 있으면 그 정지 기간으로 맞춘다" {
            val tester = saveMember("테스터")
            val startsAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusDays(1)
            val manualSuspension = sanctionRepository.save(
                SanctionFixture.create(
                    tester.id,
                    origin = SanctionOrigin.MANUAL,
                    level = SanctionLevel.SUSPENSION,
                    startsAt = startsAt,
                    endsAt = startsAt.plusDays(14),
                ),
            )
            banByDummyReport(tester, saveMember("dummy-female-aaaa"))

            adminDummyService.deleteAllDummies()

            sanctionRepository.findAll().map { it.id } shouldBe listOf(manualSuspension.id)
            memberRepository.findByIdOrNull(tester.id).shouldNotBeNull().let {
                it.status shouldBe MemberStatus.SUSPENDED
                it.suspendedUntil shouldBe manualSuspension.endsAt
            }
        }
    }
})
