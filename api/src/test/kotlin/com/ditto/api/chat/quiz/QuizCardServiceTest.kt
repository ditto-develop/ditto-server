package com.ditto.api.chat.quiz

import com.ditto.api.support.IntegrationTest
import com.ditto.api.support.QuizSeeder
import com.ditto.api.support.SeededQuizSet
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatQuizCardRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.entity.FeatureFlag
import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class QuizCardServiceTest(
    private val quizCardService: QuizCardService,
    private val featureFlags: FeatureFlags,
    private val featureFlagRepository: FeatureFlagRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatQuizCardRepository: ChatQuizCardRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val objectMapper: ObjectMapper,
    quizSetRepository: QuizSetRepository,
    quizRepository: QuizRepository,
    quizChoiceRepository: QuizChoiceRepository,
    quizAnswerRepository: QuizAnswerRepository,
    quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val seeder = QuizSeeder(quizSetRepository, quizRepository, quizChoiceRepository, quizAnswerRepository, quizProgressRepository)

    fun enable() {
        featureFlagRepository.save(FeatureFlag.of(FeatureFlagKey.QUIZ_CHAT).apply { enable(LocalDateTime.of(2026, 1, 1, 0, 0), 1L) })
        featureFlags.evict()
    }

    beforeTest { featureFlags.evict() }
    afterTest { featureFlags.evict() }

    fun roomWith(room: ChatRoom, vararg memberIds: Long): ChatRoom =
        chatRoomRepository.save(room).also { saved ->
            chatRoomMemberRepository.saveAll(memberIds.map { ChatRoomMember.of(saved.id, it) })
        }

    fun personalRoom(): Pair<ChatRoom, SeededQuizSet> {
        val set = seeder.quizSet(questions = 2, hintQuestions = listOf("왜요?", null))
        seeder.answer(1L, set, listOf(1, 2))
        seeder.answer(2L, set, listOf(2, 2))
        val match = personalMatchRepository.save(PersonalMatchFixture.create(1L, 2L, set.quizSetId))
        return roomWith(ChatRoomFixture.personal(sourceId = match.id), 1L, 2L) to set
    }

    "1:1 방에 카드를 올리면 카드 → 한마디 순으로 저장하고, 카드에는 서버가 만든 두 사람의 선택을 싣는다" {
        enable()
        val (room, set) = personalRoom()

        val result = quizCardService.send(senderId = 1L, roomId = room.id, quizId = set.quizIds[0], comment = "  왜 그 답이에요?  ")

        result.rejectedAsDuplicate shouldBe false
        result.messages.map { it.messageType } shouldBe listOf(ChatMessageType.QUIZ_CARD, ChatMessageType.TEXT)
        result.messages[1].content shouldBe "왜 그 답이에요?"
        result.messages[0].unreadCount shouldBe 1
        val card = objectMapper.readTree(result.messages[0].content)
        card["quizId"].asLong() shouldBe set.quizIds[0]
        card["senderChoiceId"].asLong() shouldBe set.choice(1, 1)
        card["peerChoiceId"].asLong() shouldBe set.choice(1, 2)
        card["hintQuestion"].asText() shouldBe "왜요?"
        card["choices"][0].has("count") shouldBe false
        chatQuizCardRepository.existsByRoomIdAndQuizId(room.id, set.quizIds[0]) shouldBe true
    }

    "한마디 없이 카드만 보낼 수 있다" {
        enable()
        val (room, set) = personalRoom()

        quizCardService.send(1L, room.id, set.quizIds[1], comment = "").messages.single().messageType shouldBe ChatMessageType.QUIZ_CARD
    }

    "이미 올라온 문항이면 카드는 버리고 한마디만 저장한다" {
        enable()
        val (room, set) = personalRoom()
        quizCardService.send(1L, room.id, set.quizIds[0], comment = "")

        val again = quizCardService.send(2L, room.id, set.quizIds[0], comment = "저도 궁금")

        again.rejectedAsDuplicate shouldBe true
        again.messages.map { it.messageType } shouldBe listOf(ChatMessageType.TEXT)
        quizCardService.send(2L, room.id, set.quizIds[0], comment = "").messages.shouldBeEmpty()
    }

    "그룹 카드는 인원 수만 싣고 누가 골랐는지는 넣지 않는다" {
        enable()
        val set = seeder.quizSet(questions = 1, matchingType = MatchingType.GROUP)
        seeder.answer(1L, set, listOf(1))
        seeder.answer(2L, set, listOf(1))
        seeder.answer(3L, set, listOf(2))
        val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = set.quizSetId))
        val room = roomWith(ChatRoomFixture.group(sourceId = group.id), 1L, 2L, 3L)

        val card = objectMapper.readTree(quizCardService.send(1L, room.id, set.quizIds[0], comment = "").messages.single().content)

        card["choices"].map { it["count"].asInt() } shouldBe listOf(2, 1)
        card.has("senderChoiceId") shouldBe false
    }

    "기능이 꺼져 있으면 0006, 방의 문항이 아니면 0001, 문항이 없으면 0001 로 막는다" {
        val (room, set) = personalRoom()

        shouldThrow<WarnException> { quizCardService.send(1L, room.id, set.quizIds[0], "") }
            .errorCode shouldBe ErrorCode.FEATURE_DISABLED

        enable()
        shouldThrow<WarnException> { quizCardService.send(1L, room.id, 99_999L, "") }.errorCode shouldBe ErrorCode.BAD_REQUEST
        shouldThrow<WarnException> { quizCardService.send(1L, room.id, null, "") }.errorCode shouldBe ErrorCode.BAD_REQUEST
        shouldThrow<WarnException> { quizCardService.send(1L, room.id, set.quizIds[0], "가".repeat(1001)) }
            .errorCode shouldBe ErrorCode.BAD_REQUEST
        chatMessageRepository.findAll().shouldBeEmpty()
    }

    "끝난 방에는 올릴 수 없다" {
        enable()
        val (room, set) = personalRoom()
        room.expire(LocalDateTime.of(2026, 3, 16, 0, 0))
        chatRoomRepository.save(room)

        shouldThrow<WarnException> { quizCardService.send(1L, room.id, set.quizIds[0], "") }
            .errorCode shouldBe ErrorCode.CHAT_ROOM_ENDED
    }
})
