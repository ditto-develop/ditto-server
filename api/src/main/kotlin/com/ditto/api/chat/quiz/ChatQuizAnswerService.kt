package com.ditto.api.chat.quiz

import com.ditto.api.chat.dto.QuizCompareItem
import com.ditto.api.chat.dto.QuizComparePercentChoice
import com.ditto.api.chat.dto.QuizCompareResponse
import com.ditto.api.chat.dto.QuizDistributionChoice
import com.ditto.api.chat.dto.QuizDistributionItem
import com.ditto.api.chat.dto.QuizDistributionResponse
import com.ditto.api.chat.service.ChatRoomAccessChecker
import com.ditto.api.quiz.stat.QuizAnswerSheet
import com.ditto.api.quiz.stat.QuizAnswerSheetLoader
import com.ditto.api.quiz.stat.QuizResultSchedule
import com.ditto.api.quiz.stat.QuizStatSnapshot
import com.ditto.api.quiz.stat.QuizStatSnapshotService
import com.ditto.api.system.ServerTimeProvider
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatQuizCardRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import org.springframework.stereotype.Service

/**
 * 대화방 답변 보기 — 1:1·재매칭은 두 사람의 문항별 답 비교(BE-3), 그룹은 인원 분포(BE-6).
 *
 * **방이 생긴 뒤부터 끝나기 전까지만** 보인다(목 매칭데이 ~ 채팅 종료, 위키 §0-3). 끝난 방은 `CHAT_ROOM_ENDED`로 막아
 * FE 가 배너·아이콘을 숨긴다.
 *
 * 1:1 비교는 상대의 문항별 선택을 공개한다 — ADR 0020("원문이 아니라 일치 요약만")을 이 경로에 한해 갱신한 결정이다(ADR 0052).
 * 그룹은 누가 무엇을 골랐는지 잇지 않는다(인원 수만).
 *
 * 트랜잭션을 열지 않는다 — 스냅샷 지연 생성이 자기 트랜잭션에서 커밋돼야 같은 요청이 그 결과를 읽는다.
 */
@Service
class ChatQuizAnswerService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatRoomAccessChecker: ChatRoomAccessChecker,
    private val chatRoomQuizSetResolver: ChatRoomQuizSetResolver,
    private val chatQuizCardRepository: ChatQuizCardRepository,
    private val memberRepository: MemberRepository,
    private val memberBlockRepository: MemberBlockRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizAnswerSheetLoader: QuizAnswerSheetLoader,
    private val quizStatSnapshotService: QuizStatSnapshotService,
    private val featureFlags: FeatureFlags,
    private val serverTimeProvider: ServerTimeProvider,
) {

    fun compare(memberId: Long, roomId: Long): QuizCompareResponse {
        val room = openRoomOf(memberId, roomId, expectGroup = false)
        val peerId = chatRoomMemberRepository.findByRoomId(roomId).firstOrNull { it.memberId != memberId }?.memberId
            ?: throw WarnException(ErrorCode.QUIZ_COMPARE_UNAVAILABLE)
        validateComparable(memberId, peerId)

        val quizSetId = quizSetIdOf(room)
        val sheet = quizAnswerSheetLoader.load(quizSetId, listOf(memberId, peerId))
        val snapshot = snapshotIfClosed(quizSetId)
        val sentQuizIds = sentQuizIds(roomId)

        val items = sheet.quizzes.map { quiz ->
            val myChoiceId = sheet.choiceOf(memberId, quiz.id)
            val peerChoiceId = sheet.choiceOf(peerId, quiz.id)
            QuizCompareItem(
                quizId = quiz.id,
                order = quiz.displayOrder,
                question = quiz.question,
                choices = sheet.choicesOf(quiz.id).map {
                    QuizComparePercentChoice(choiceId = it.id, content = it.content, percent = snapshot?.percentOf(it.id) ?: 0)
                },
                myChoiceId = myChoiceId,
                peerChoiceId = peerChoiceId,
                same = myChoiceId != null && myChoiceId == peerChoiceId,
                minority = myChoiceId != null && snapshot?.isMinority(myChoiceId) == true,
                hintQuestion = quiz.hintQuestion,
                sent = quiz.id in sentQuizIds,
            )
        }
        val sameCount = items.count { it.same }

        return QuizCompareResponse(
            quizSetId = quizSetId,
            totalCount = items.size,
            sameCount = sameCount,
            diffCount = items.size - sameCount,
            highlightQuizId = items
                .filter { it.same && it.minority }
                .minWithOrNull(compareBy({ snapshot?.percentOf(it.myChoiceId!!) ?: 0 }, { it.order }))
                ?.quizId,
            canSendCard = canSendCard(room),
            chatOpensAt = room.opensAt,
            items = items,
        )
    }

    fun distribution(memberId: Long, roomId: Long): QuizDistributionResponse {
        val room = openRoomOf(memberId, roomId, expectGroup = true)
        val activeMemberIds = chatRoomMemberRepository.findByRoomId(roomId).filterNot(ChatRoomMember::hasLeft).map { it.memberId }

        val quizSetId = quizSetIdOf(room)
        val sheet = quizAnswerSheetLoader.load(quizSetId, activeMemberIds)
        val sentQuizIds = sentQuizIds(roomId)

        val items = sheet.quizzes.map { quiz ->
            val counts = sheet.countsAmong(quiz.id, activeMemberIds)
            QuizDistributionItem(
                quizId = quiz.id,
                order = quiz.displayOrder,
                question = quiz.question,
                choices = sheet.choicesOf(quiz.id).map {
                    QuizDistributionChoice(choiceId = it.id, content = it.content, count = counts[it.id] ?: 0)
                },
                myChoiceId = sheet.choiceOf(memberId, quiz.id),
                unanimous = sheet.spreadAmong(quiz.id, activeMemberIds).unanimous,
                hintQuestion = quiz.hintQuestion,
                sent = quiz.id in sentQuizIds,
            )
        }
        val unanimousCount = items.count { it.unanimous }

        return QuizDistributionResponse(
            quizSetId = quizSetId,
            memberCount = activeMemberIds.size,
            totalCount = items.size,
            unanimousCount = unanimousCount,
            splitCount = items.size - unanimousCount,
            splitQuizIds = items
                .filterNot { it.unanimous }
                .sortedWith(compareBy({ spreadOf(it).gap }, { it.order }))
                .take(GroupAnswerSpread.SPLIT_QUIZ_LIMIT)
                .map { it.quizId },
            canSendCard = canSendCard(room),
            chatOpensAt = room.opensAt,
            items = items,
        )
    }

    /** 질문 카드를 보낼 수 있는 방인지. 화면의 버튼 상태와 실제 전송 검사가 같은 조건을 봐야 한다. */
    fun canSendCard(room: ChatRoom): Boolean =
        room.status == ChatRoomStatus.ACTIVE && featureFlags.isEnabled(FeatureFlagKey.QUIZ_CHAT)

    private fun spreadOf(item: QuizDistributionItem) = GroupAnswerSpread(item.choices.map { it.count })

    /** 멤버이고, 방 유형이 맞고, 끝나지 않은 방. 멤버 검사를 먼저 해 남의 방 유형이 새지 않게 한다. */
    private fun openRoomOf(memberId: Long, roomId: Long, expectGroup: Boolean): ChatRoom {
        chatRoomAccessChecker.validateMember(roomId, memberId)
        val room = chatRoomRepository.findById(roomId).orElseThrow { chatRoomAccessChecker.notFoundOrForbidden(roomId) }
        if ((room.sourceType == ChatRoomType.GROUP) != expectGroup) {
            throw WarnException(ErrorCode.QUIZ_ANSWER_VIEW_NOT_SUPPORTED)
        }
        if (room.isEnded) {
            throw WarnException(ErrorCode.CHAT_ROOM_ENDED)
        }
        return room
    }

    /** 상대가 탈퇴했거나 어느 쪽이든 차단했으면 답을 보여 주지 않는다. 차단 화면 문구("나의 프로필을 볼 수 없고")와 같은 결이다. */
    private fun validateComparable(memberId: Long, peerId: Long) {
        val peer = memberRepository.findById(peerId).orElse(null)
        if (peer == null || peer.isLeft() ||
            memberBlockRepository.existsByBlockerIdAndBlockedMemberId(memberId, peerId) ||
            memberBlockRepository.existsByBlockerIdAndBlockedMemberId(peerId, memberId)
        ) {
            throw WarnException(ErrorCode.QUIZ_COMPARE_UNAVAILABLE)
        }
    }

    private fun quizSetIdOf(room: ChatRoom): Long =
        chatRoomQuizSetResolver.quizSetIdOf(room) ?: throw WarnException(ErrorCode.NOT_FOUND, "방의 퀴즈셋을 찾을 수 없습니다.")

    /**
     * 마감된 셋의 스냅샷. 방은 목요일 매칭 뒤에 생겨 보통 이미 마감돼 있다. 마감 전이면(QA 로 만든 방 등) 스냅샷을 만들지 않는다 —
     * 그 순간까지의 답으로 굳어 버린다. 그때 비율은 0, 소수 의견은 없음으로 답한다.
     */
    private fun snapshotIfClosed(quizSetId: Long): QuizStatSnapshot? {
        val quizSet = quizSetRepository.findById(quizSetId).orElse(null) ?: return null
        if (!QuizResultSchedule.isOpen(quizSet, serverTimeProvider.now())) return null
        return quizStatSnapshotService.getOrCreate(quizSetId)
    }

    private fun sentQuizIds(roomId: Long): Set<Long> = chatQuizCardRepository.findByRoomId(roomId).map { it.quizId }.toSet()
}

/** [QuizAnswerSheet]에서 한 문항의 그룹 분포를 꺼낸다. */
internal fun QuizAnswerSheet.spreadAmong(quizId: Long, memberIds: Collection<Long>): GroupAnswerSpread {
    val counts = countsAmong(quizId, memberIds)
    return GroupAnswerSpread(choicesOf(quizId).map { counts[it.id] ?: 0 })
}
