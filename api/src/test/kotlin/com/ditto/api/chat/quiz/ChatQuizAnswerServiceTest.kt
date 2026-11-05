package com.ditto.api.chat.quiz

import com.ditto.api.support.IntegrationTest
import com.ditto.api.support.QuizSeeder
import com.ditto.api.system.featureflag.FeatureFlagAdminService
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.chat.entity.ChatQuizCard
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.repository.ChatQuizCardRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class ChatQuizAnswerServiceTest(
    private val chatQuizAnswerService: ChatQuizAnswerService,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatQuizCardRepository: ChatQuizCardRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val rematchRepository: RematchRepository,
    private val memberRepository: MemberRepository,
    private val memberBlockRepository: MemberBlockRepository,
    private val featureFlags: FeatureFlags,
    private val featureFlagAdminService: FeatureFlagAdminService,
    quizSetRepository: QuizSetRepository,
    quizRepository: QuizRepository,
    quizChoiceRepository: QuizChoiceRepository,
    quizAnswerRepository: QuizAnswerRepository,
    quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val seeder = QuizSeeder(quizSetRepository, quizRepository, quizChoiceRepository, quizAnswerRepository, quizProgressRepository)
    var seq = 0

    beforeTest { featureFlags.evict() }
    afterTest { featureFlags.evict() }

    fun member() = memberRepository.save(MemberFixture.create(nickname = "회원${seq++}", status = MemberStatus.ACTIVE)).id

    fun roomWith(room: ChatRoom, vararg memberIds: Long): ChatRoom =
        chatRoomRepository.save(room).also { saved ->
            chatRoomMemberRepository.saveAll(memberIds.map { ChatRoomMember.of(saved.id, it) })
        }

    "1:1 답변 비교" - {
        "문항별 내 답·상대 답·같은지와 전체 비율을 주고, 같은 답 중 소수 의견을 강조한다" {
            val me = member()
            val peer = member()
            val set = seeder.quizSet(questions = 3, hintQuestions = listOf("왜요?", null, null))
            seeder.answer(me, set, listOf(2, 1, 2))
            seeder.answer(peer, set, listOf(2, 1, 1))
            repeat(18) { seeder.answer(member(), set, listOf(1, 1, 1)) }
            val match = personalMatchRepository.save(PersonalMatchFixture.create(me, peer, set.quizSetId))
            val room = roomWith(ChatRoomFixture.personal(sourceId = match.id), me, peer)
            chatQuizCardRepository.save(ChatQuizCard.of(room.id, set.quizIds[1], messageId = 1L, senderId = me))

            val result = chatQuizAnswerService.compare(me, room.id)

            result.totalCount shouldBe 3
            result.sameCount shouldBe 2
            result.diffCount shouldBe 1
            result.highlightQuizId shouldBe set.quizIds[0]
            result.items[0].let {
                it.myChoiceId shouldBe set.choice(1, 2)
                it.peerChoiceId shouldBe set.choice(1, 2)
                it.same shouldBe true
                it.minority shouldBe true
                it.choices.map { c -> c.percent } shouldBe listOf(90, 10)
                it.hintQuestion shouldBe "왜요?"
            }
            result.items[1].sent shouldBe true
            result.items[2].same shouldBe false
            result.chatOpensAt shouldBe room.opensAt
        }

        "기능이 꺼져 있으면 열린 방이어도 카드를 보낼 수 없다고 답하고, 켜면 보낼 수 있다고 답한다" {
            val me = member()
            val peer = member()
            val set = seeder.quizSet(questions = 1)
            seeder.answer(me, set, listOf(1))
            seeder.answer(peer, set, listOf(1))
            val match = personalMatchRepository.save(PersonalMatchFixture.create(me, peer, set.quizSetId))
            val room = roomWith(ChatRoomFixture.personal(sourceId = match.id), me, peer)

            chatQuizAnswerService.compare(me, room.id).canSendCard shouldBe false

            featureFlagAdminService.set(FeatureFlagKey.QUIZ_CHAT, enabled = true, adminMemberId = 1L)
            chatQuizAnswerService.compare(me, room.id).canSendCard shouldBe true
        }

        "아직 열리지 않은 방은 비교는 보이고 카드는 보낼 수 없다" {
            featureFlagAdminService.set(FeatureFlagKey.QUIZ_CHAT, enabled = true, adminMemberId = 1L)
            val me = member()
            val peer = member()
            val set = seeder.quizSet(questions = 1)
            val match = personalMatchRepository.save(PersonalMatchFixture.create(me, peer, set.quizSetId))
            val thursday = LocalDateTime.of(2026, 10, 29, 9, 0)
            val room = roomWith(ChatRoomFixture.personal(sourceId = match.id, now = thursday, period = ChatPeriod.weekendOf(thursday)), me, peer)

            val result = chatQuizAnswerService.compare(me, room.id)

            result.canSendCard shouldBe false
            result.chatOpensAt shouldBe LocalDateTime.of(2026, 10, 30, 0, 0)
        }

        "재매칭 방은 원래 그룹 퀴즈 기준으로 비교한다" {
            val me = member()
            val peer = member()
            val groupSet = seeder.quizSet(questions = 2, matchingType = MatchingType.GROUP)
            seeder.answer(me, groupSet, listOf(1, 2))
            seeder.answer(peer, groupSet, listOf(1, 1))
            val rematch = rematchRepository.save(RematchFixture.create(quizSetId = groupSet.quizSetId, memberIdA = me, memberIdB = peer))
            val room = roomWith(ChatRoomFixture.rematch(sourceId = rematch.id), me, peer)

            val result = chatQuizAnswerService.compare(me, room.id)

            result.quizSetId shouldBe groupSet.quizSetId
            result.sameCount shouldBe 1
        }

        "끝난 방은 7004, 상대가 탈퇴했거나 차단 관계면 7008, 그룹 방은 7009, 남의 방은 7002 로 막는다" {
            val me = member()
            val peer = member()
            val set = seeder.quizSet(questions = 1)
            val match = personalMatchRepository.save(PersonalMatchFixture.create(me, peer, set.quizSetId))
            val room = roomWith(ChatRoomFixture.personal(sourceId = match.id), me, peer)
            val ended = roomWith(ChatRoomFixture.personal(sourceId = match.id + 1).apply { expire(LocalDateTime.of(2026, 3, 16, 0, 0)) }, me, peer)
            val group = roomWith(ChatRoomFixture.group(sourceId = 77L), me, peer)

            shouldThrow<WarnException> { chatQuizAnswerService.compare(me, ended.id) }.errorCode shouldBe ErrorCode.CHAT_ROOM_ENDED
            shouldThrow<WarnException> { chatQuizAnswerService.compare(me, group.id) }.errorCode shouldBe ErrorCode.QUIZ_ANSWER_VIEW_NOT_SUPPORTED
            shouldThrow<WarnException> { chatQuizAnswerService.compare(member(), room.id) }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER

            memberBlockRepository.save(MemberBlock.create(blockerId = peer, blockedMemberId = me))
            shouldThrow<WarnException> { chatQuizAnswerService.compare(me, room.id) }.errorCode shouldBe ErrorCode.QUIZ_COMPARE_UNAVAILABLE
        }

        "상대가 탈퇴했으면 비교할 수 없다" {
            val me = member()
            val peer = memberRepository.save(MemberFixture.create(nickname = "회원${seq++}", status = MemberStatus.LEFT)).id
            val set = seeder.quizSet(questions = 1)
            val match = personalMatchRepository.save(PersonalMatchFixture.create(me, peer, set.quizSetId))
            val room = roomWith(ChatRoomFixture.personal(sourceId = match.id), me, peer)

            shouldThrow<WarnException> { chatQuizAnswerService.compare(me, room.id) }.errorCode shouldBe ErrorCode.QUIZ_COMPARE_UNAVAILABLE
        }
    }

    "그룹 분포" - {
        "남아 있는 멤버의 선택지별 인원만 주고, 만장일치·반반 갈린 질문을 센다" {
            val members = List(4) { member() }
            val left = member()
            val set = seeder.quizSet(questions = 3, matchingType = MatchingType.GROUP)
            // 문항 1: 4:0 만장일치 / 문항 2: 2:2 / 문항 3: 3:1
            members.forEachIndexed { i, id -> seeder.answer(id, set, listOf(1, if (i < 2) 1 else 2, if (i < 3) 1 else 2)) }
            seeder.answer(left, set, listOf(2, 2, 2))
            val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = set.quizSetId))
            val room = roomWith(ChatRoomFixture.group(sourceId = group.id), *members.toLongArray(), left)
            chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, left)!!.let {
                it.leave(LocalDateTime.of(2026, 3, 13, 13, 0))
                chatRoomMemberRepository.save(it)
            }

            val result = chatQuizAnswerService.distribution(members[0], room.id)

            result.memberCount shouldBe 4
            result.unanimousCount shouldBe 1
            result.splitCount shouldBe 2
            result.splitQuizIds shouldBe listOf(set.quizIds[1], set.quizIds[2])
            result.items[0].choices.map { it.count } shouldBe listOf(4, 0)
            result.items[0].unanimous shouldBe true
            result.items[1].myChoiceId shouldBe set.choice(2, 1)
        }

        "1:1 방에서 부르면 7009 로 막는다" {
            val me = member()
            val room = roomWith(ChatRoomFixture.personal(sourceId = 5L), me, member())

            shouldThrow<WarnException> { chatQuizAnswerService.distribution(me, room.id) }
                .errorCode shouldBe ErrorCode.QUIZ_ANSWER_VIEW_NOT_SUPPORTED
        }
    }
})
