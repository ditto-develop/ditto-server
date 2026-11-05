package com.ditto.api.chat.quiz

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.chat.service.ChatRoomAccessChecker
import com.ditto.api.quiz.stat.QuizAnswerSheet
import com.ditto.api.quiz.stat.QuizAnswerSheetLoader
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatQuizCard
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatQuizCardRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.system.entity.FeatureFlagKey
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 질문 카드 전송(IMP-1 BE-5). 카드와 덧붙인 한마디를 **한 트랜잭션에서 카드 → 한마디 순으로** 저장해, 둘의 순서를
 * 클라이언트가 아니라 서버가 정한다.
 *
 * 카드 내용은 서버가 만든다 — 문항·선택지·답을 클라이언트 값으로 받지 않는다. 보낸 시점 값으로 굳는다.
 * 같은 방에 같은 문항은 한 번만 올라간다. 이미 올라온 문항이면 카드는 버리고 한마디만 저장한다.
 */
@Service
@Transactional(readOnly = true)
class QuizCardService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatQuizCardRepository: ChatQuizCardRepository,
    private val chatRoomAccessChecker: ChatRoomAccessChecker,
    private val chatRoomQuizSetResolver: ChatRoomQuizSetResolver,
    private val quizAnswerSheetLoader: QuizAnswerSheetLoader,
    private val featureFlags: FeatureFlags,
    private val objectMapper: ObjectMapper,
) {

    /**
     * **방 행을 잠그고 시작한다.** 두 사람이 같은 문항을 동시에 올리면 둘 다 "아직 없다"로 보고 카드를 넣다가 한쪽이
     * 유니크에 걸려 한마디까지 롤백되고 7007 도 못 받는다. 잠금이 둘을 줄 세워 뒤 요청이 앞 카드를 보게 한다.
     * 잠금 읽기를 이 트랜잭션의 첫 조회로 둬야 그 뒤 읽기(MySQL REPEATABLE READ 스냅샷)가 앞 요청의 커밋을 본다.
     * 잠금 순서는 다른 방 전이와 같은 방 → 멤버다.
     */
    @Transactional
    fun send(senderId: Long, roomId: Long, quizId: Long, comment: String): QuizCardSendResult {
        val room = chatRoomRepository.findWithLockById(roomId) ?: throw chatRoomAccessChecker.notFoundOrForbidden(roomId)
        chatRoomAccessChecker.validateActiveMember(roomId, senderId)
        if (!featureFlags.isEnabled(FeatureFlagKey.QUIZ_CHAT)) {
            throw WarnException(ErrorCode.FEATURE_DISABLED)
        }
        val trimmedComment = comment.trim()
        if (trimmedComment.length > ChatMessage.MAX_CONTENT_LENGTH) {
            throw WarnException(ErrorCode.BAD_REQUEST)
        }

        val roomMembers = chatRoomMemberRepository.findByRoomId(roomId)
        val activeMemberIds = roomMembers.filterNot(ChatRoomMember::hasLeft).map { it.memberId }
        val quizSetId = chatRoomQuizSetResolver.requireQuizSetIdOf(room)
        val sheet = quizAnswerSheetLoader.load(quizSetId, activeMemberIds)
        // 그 방의 퀴즈셋 문항이어야 한다. 다른 주·다른 셋 문항을 올리면 답 비교가 성립하지 않는다.
        val quiz = sheet.quiz(quizId) ?: throw WarnException(ErrorCode.QUIZ_NOT_IN_ROOM)

        val duplicate = chatQuizCardRepository.existsByRoomIdAndQuizId(roomId, quizId)
        val saved = buildList {
            if (!duplicate) {
                val body = if (room.sourceType == ChatRoomType.GROUP) {
                    groupCard(quiz, sheet, activeMemberIds)
                } else {
                    pairCard(quiz, sheet, senderId, activeMemberIds.firstOrNull { it != senderId })
                }
                val card = chatMessageRepository.save(
                    ChatMessage.of(roomId, senderId, objectMapper.writeValueAsString(body), ChatMessageType.QUIZ_CARD),
                )
                chatQuizCardRepository.save(ChatQuizCard.of(roomId, quizId, card.id, senderId))
                add(card)
            }
            if (trimmedComment.isNotEmpty()) {
                add(chatMessageRepository.save(ChatMessage.of(roomId, senderId, trimmedComment)))
            }
        }

        return QuizCardSendResult(
            messages = saved.map { ChatMessageResponse.of(it, imageUrl = null, roomMembers = roomMembers) },
            rejectedAsDuplicate = duplicate,
        )
    }

    /** 1:1 카드. 보낸 사람·상대 선택을 함께 싣고, FE 가 보는 사람 기준으로 '나/상대'를 바꿔 그린다. */
    private fun pairCard(quiz: Quiz, sheet: QuizAnswerSheet, senderId: Long, peerId: Long?) = PairQuizCard(
        quizId = quiz.id,
        question = quiz.question,
        choices = sheet.choicesOf(quiz.id).map { QuizCardChoice(choiceId = it.id, content = it.content, count = null) },
        senderChoiceId = sheet.choiceOf(senderId, quiz.id),
        peerChoiceId = peerId?.let { sheet.choiceOf(it, quiz.id) },
        hintQuestion = quiz.hintQuestion,
    )

    /** 그룹 카드. 인원 수만 싣는다 — 누가(보낸 사람 포함) 무엇을 골랐는지는 넣지 않는다. */
    private fun groupCard(quiz: Quiz, sheet: QuizAnswerSheet, memberIds: List<Long>): GroupQuizCard {
        val counts = sheet.countsAmong(quiz.id, memberIds)
        return GroupQuizCard(
            quizId = quiz.id,
            question = quiz.question,
            choices = sheet.choicesOf(quiz.id).map { QuizCardChoice(choiceId = it.id, content = it.content, count = counts[it.id] ?: 0) },
            hintQuestion = quiz.hintQuestion,
        )
    }
}

data class QuizCardSendResult(
    /** 저장 순서(카드 → 한마디). 둘 다 없을 수도 있다(이미 올라온 문항 + 한마디 없음). */
    val messages: List<ChatMessageResponse>,
    /** 이미 올라온 문항이라 카드를 버렸다. 보낸 사람에게 `7007`을 알린다. */
    val rejectedAsDuplicate: Boolean,
)

data class PairQuizCard(
    val quizId: Long,
    val question: String,
    val choices: List<QuizCardChoice>,
    val senderChoiceId: Long?,
    val peerChoiceId: Long?,
    val hintQuestion: String?,
)

data class GroupQuizCard(
    val quizId: Long,
    val question: String,
    val choices: List<QuizCardChoice>,
    val hintQuestion: String?,
)

data class QuizCardChoice(
    val choiceId: Long,
    val content: String,
    /** 그룹 카드만. 1:1 은 null. */
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val count: Int?,
)
