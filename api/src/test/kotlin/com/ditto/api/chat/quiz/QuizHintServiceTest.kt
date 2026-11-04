package com.ditto.api.chat.quiz

import com.ditto.api.support.IntegrationTest
import com.ditto.api.support.QuizSeeder
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatQuizHintRepository
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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private val NOW = LocalDateTime.of(2026, 3, 13, 12, 0)

class QuizHintServiceTest(
    private val quizHintService: QuizHintService,
    private val featureFlags: FeatureFlags,
    private val featureFlagRepository: FeatureFlagRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatQuizHintRepository: ChatQuizHintRepository,
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

    beforeTest { featureFlags.evict() }
    afterTest { featureFlags.evict() }

    fun enableSince(at: LocalDateTime) {
        featureFlagRepository.save(FeatureFlag.of(FeatureFlagKey.QUIZ_CHAT).apply { enable(at, 1L) })
        featureFlags.evict()
    }

    fun roomWith(room: ChatRoom, vararg memberIds: Long): ChatRoom =
        chatRoomRepository.save(room).also { saved ->
            chatRoomMemberRepository.saveAll(memberIds.map { ChatRoomMember.of(saved.id, it) })
        }

    fun personalRoom(answersA: List<Int>, answersB: List<Int>): Pair<ChatRoom, com.ditto.api.support.SeededQuizSet> {
        val set = seeder.quizSet(questions = answersA.size, hintQuestions = List(answersA.size) { "힌트${it + 1}" })
        seeder.answer(1L, set, answersA)
        seeder.answer(2L, set, answersB)
        val match = personalMatchRepository.save(PersonalMatchFixture.create(1L, 2L, set.quizSetId))
        return roomWith(ChatRoomFixture.personal(sourceId = match.id), 1L, 2L) to set
    }

    "기능이 꺼져 있으면 힌트를 만들지 않는다" {
        personalRoom(listOf(1, 1), listOf(1, 2))

        quizHintService.createMissing(NOW).shouldBeEmpty()
        chatMessageRepository.findAll().shouldBeEmpty()
    }

    "켠 뒤 열린 1:1 방에 보낸 사람 없는 힌트를 하나 남기고, 다시 돌려도 더 만들지 않는다" {
        enableSince(LocalDateTime.of(2026, 3, 12, 0, 0))
        val (room, set) = personalRoom(listOf(1, 2), listOf(2, 2))

        val created = quizHintService.createMissing(NOW)

        created shouldHaveSize 1
        created.single().let {
            it.roomId shouldBe room.id
            it.senderId.shouldBeNull()
            it.messageType shouldBe ChatMessageType.QUIZ_HINT
            it.unreadCount shouldBe 0
            val content = objectMapper.readTree(it.content)
            content["quizId"].asLong() shouldBe set.quizIds[1]
            content["kind"].asText() shouldBe "SAME"
            content["answer"].asText() shouldBe "Q2-2"
            content["hintQuestion"].asText() shouldBe "힌트2"
        }
        quizHintService.createMissing(NOW).shouldBeEmpty()
        chatQuizHintRepository.existsByRoomId(room.id) shouldBe true
    }

    "켜기 전부터 열려 있던 방에는 소급해 만들지 않는다" {
        val (_, _) = personalRoom(listOf(1), listOf(1))
        enableSince(LocalDateTime.now().plusDays(1))

        quizHintService.createMissing(NOW).shouldBeEmpty()
    }

    "그룹 방은 만장일치 문항을 UNANIMOUS 로 남긴다" {
        enableSince(LocalDateTime.of(2026, 3, 12, 0, 0))
        val set = seeder.quizSet(questions = 2, matchingType = MatchingType.GROUP)
        listOf(1L, 2L, 3L).forEach { seeder.answer(it, set, listOf(1, if (it == 1L) 1 else 2)) }
        val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = set.quizSetId))
        roomWith(ChatRoomFixture.group(sourceId = group.id), 1L, 2L, 3L)

        val content = objectMapper.readTree(quizHintService.createMissing(NOW).single().content)

        content["kind"].asText() shouldBe "UNANIMOUS"
        content["quizId"].asLong() shouldBe set.quizIds[0]
    }

    "원본 매칭이 없어 고를 문항이 없으면 메시지 없이 표시만 남겨 다시 집지 않는다" {
        enableSince(LocalDateTime.of(2026, 3, 12, 0, 0))
        val room = roomWith(ChatRoomFixture.personal(sourceId = 999L), 1L, 2L)

        quizHintService.createMissing(NOW).shouldBeEmpty()

        chatQuizHintRepository.existsByRoomId(room.id) shouldBe true
        chatMessageRepository.findAll().shouldBeEmpty()
    }
})
