package com.ditto.api.admin.quiz

import com.ditto.api.admin.cleanup.MatchingRecordEraser
import com.ditto.api.config.AdminQaToolsProperties
import com.ditto.api.match.MatchWeekPolicy
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.repository.MemberReviewRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class AdminQuizQaServiceTest(
    private val adminQuizQaService: AdminQuizQaService,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val rematchRepository: RematchRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val notificationRepository: NotificationRepository,
    private val quizSetMatchingTargetFinder: QuizSetMatchingTargetFinder,
    private val matchingRecordEraser: MatchingRecordEraser,
    private val quizSetDeleter: QuizSetDeleter,
    private val matchWeekPolicy: MatchWeekPolicy,
    private val memberRepository: MemberRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    // 1:1 후보·신청·방, 그룹·초대·방, 재매칭·방, 방의 메시지·평가, 알림까지 갖춘 매칭이 끝난 퀴즈셋.
    fun saveMatchedQuizSet(quizSet: QuizSet = QuizSetFixture.currentWeek()): Long {
        val quizSetId = quizSetRepository.save(quizSet).id
        quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = quizSetId))
        matchCandidateRepository.save(MatchCandidateFixture.create(1L, 2L, quizSetId))
        matchCandidateRepository.save(MatchCandidateFixture.create(2L, 1L, quizSetId))

        val personalMatch = personalMatchRepository.save(PersonalMatchFixture.create(1L, 2L, quizSetId))
        val personalRoom = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = personalMatch.id))
        chatMessageRepository.save(ChatMessageFixture.create(roomId = personalRoom.id, senderId = 1L))
        memberReviewRepository.save(MemberReviewFixture.create(authorMemberId = 1L, chatRoomId = personalRoom.id))

        val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))
        (3L..5L).forEach { groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, it)) }
        val groupRoom = chatRoomRepository.save(ChatRoomFixture.group(sourceId = group.id))
        val rematch = rematchRepository.save(
            RematchFixture.create(sourceGroupMatchId = group.id, sourceChatRoomId = groupRoom.id, quizSetId = quizSetId),
        )
        chatRoomRepository.save(ChatRoomFixture.rematch(sourceId = rematch.id))

        notificationRepository.save(NotificationFixture.create(type = NotificationType.MATCH_RESULT, targetId = quizSetId))
        notificationRepository.save(
            NotificationFixture.create(memberId = 2L, type = NotificationType.MATCH_REQUESTED, targetId = personalMatch.id),
        )
        notificationRepository.save(
            NotificationFixture.create(memberId = 3L, type = NotificationType.CHAT_ROOM_OPENED, targetId = groupRoom.id),
        )
        return quizSetId
    }

    "매칭 기록 초기화" - {
        "매칭 기록만 지우고 퀴즈셋과 진행은 남긴다" {
            val quizSetId = saveMatchedQuizSet()

            val summary = adminQuizQaService.resetMatching(quizSetId)

            matchCandidateRepository.existsByQuizSetId(quizSetId) shouldBe false
            personalMatchRepository.existsByQuizSetId(quizSetId) shouldBe false
            groupMatchRepository.existsByQuizSetId(quizSetId) shouldBe false
            groupMatchMemberRepository.findAll().shouldBeEmpty()
            rematchRepository.findAll().shouldBeEmpty()
            chatRoomRepository.findAll().shouldBeEmpty()
            chatMessageRepository.findAll().shouldBeEmpty()
            memberReviewRepository.findAll().shouldBeEmpty()
            notificationRepository.findAll().shouldBeEmpty()
            quizSetRepository.existsById(quizSetId) shouldBe true
            quizProgressRepository.findAll() shouldHaveSize 1
            summary.counts.candidateRowCount shouldBe 2
            summary.counts.roomCount shouldBe 3
            summary.notificationCount shouldBe 3
        }

        "다른 퀴즈셋의 매칭 기록은 건드리지 않는다" {
            val quizSetId = saveMatchedQuizSet()
            val otherQuizSetId = quizSetRepository.save(QuizSetFixture.currentWeek()).id
            matchCandidateRepository.save(MatchCandidateFixture.create(7L, 8L, otherQuizSetId))
            val otherMatch = personalMatchRepository.save(PersonalMatchFixture.create(7L, 8L, otherQuizSetId))
            val otherRoom = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = otherMatch.id))

            adminQuizQaService.resetMatching(quizSetId)

            matchCandidateRepository.existsByQuizSetId(otherQuizSetId) shouldBe true
            personalMatchRepository.existsById(otherMatch.id) shouldBe true
            chatRoomRepository.findAll().map { it.id } shouldBe listOf(otherRoom.id)
        }

        "지난 주 퀴즈셋은 초기화를 거부하고 아무것도 지우지 않는다" {
            val quizSetId = saveMatchedQuizSet(QuizSetFixture.create())

            val exception = shouldThrow<WarnException> { adminQuizQaService.resetMatching(quizSetId) }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            matchCandidateRepository.existsByQuizSetId(quizSetId) shouldBe true
        }

        "없는 퀴즈셋이면 NOT_FOUND 예외가 발생한다" {
            val exception = shouldThrow<WarnException> { adminQuizQaService.resetMatching(99999L) }

            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }

    "강제 삭제" - {
        "지난 주 퀴즈셋도 강제 삭제는 된다" {
            val quizSetId = saveMatchedQuizSet(QuizSetFixture.create())

            val summary = adminQuizQaService.forceDelete(quizSetId)

            quizSetRepository.existsById(quizSetId) shouldBe false
            summary.quizSetTitle shouldBe "이번 주 1:1 매칭"
        }

        "일반 삭제가 거부되는 퀴즈셋도 매칭 기록과 함께 지운다" {
            val quizSetId = saveMatchedQuizSet()
            shouldThrow<WarnException> { quizSetDeleter.delete(quizSetId) }

            adminQuizQaService.forceDelete(quizSetId)

            quizSetRepository.existsById(quizSetId) shouldBe false
            quizProgressRepository.findAll().shouldBeEmpty()
            chatRoomRepository.findAll().shouldBeEmpty()
        }
    }

    "미리보기" - {
        "지우지 않고 지울 개수와 실회원이 낀 방 수를 세고 이번 주 셋이면 초기화할 수 있다고 알려 준다" {
            val quizSetId = saveMatchedQuizSet()
            val real = memberRepository.save(MemberFixture.create(nickname = "실회원", email = "real@example.com"))
            val dummy = memberRepository.save(MemberFixture.create(nickname = "dummy-male-0001", email = "d@dummy.local"))
            val (realRoom, dummyOnlyRoom) = chatRoomRepository.findAll().take(2)
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = realRoom.id, memberId = real.id))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = realRoom.id, memberId = dummy.id))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = dummyOnlyRoom.id, memberId = dummy.id))

            val preview = adminQuizQaService.previewErase(quizSetId)

            preview.counts.candidateRowCount shouldBe 2
            preview.counts.roomCount shouldBe 3
            preview.realMemberRoomCount shouldBe 1
            preview.isResettable shouldBe true
            chatRoomRepository.findAll() shouldHaveSize 3
        }

        "지난 주 셋은 초기화할 수 없다고 알려 준다" {
            val quizSetId = saveMatchedQuizSet(QuizSetFixture.create())

            adminQuizQaService.previewErase(quizSetId).isResettable shouldBe false
        }
    }

    "QA 도구 스위치" - {
        "꺼져 있으면 아무것도 지우지 않고 거부한다" {
            val quizSetId = saveMatchedQuizSet()
            val disabledService = AdminQuizQaService(
                adminQaToolsProperties = AdminQaToolsProperties(enabled = false),
                quizSetRepository = quizSetRepository,
                matchCandidateRepository = matchCandidateRepository,
                quizSetMatchingTargetFinder = quizSetMatchingTargetFinder,
                matchingRecordEraser = matchingRecordEraser,
                quizSetDeleter = quizSetDeleter,
                matchWeekPolicy = matchWeekPolicy,
            )

            val resetException = shouldThrow<WarnException> { disabledService.resetMatching(quizSetId) }
            val deleteException = shouldThrow<WarnException> { disabledService.forceDelete(quizSetId) }

            resetException.errorCode shouldBe ErrorCode.FORBIDDEN
            deleteException.errorCode shouldBe ErrorCode.FORBIDDEN
            quizSetRepository.existsById(quizSetId) shouldBe true
            matchCandidateRepository.existsByQuizSetId(quizSetId) shouldBe true
            chatRoomRepository.findAll() shouldHaveSize 3
        }
    }
})
