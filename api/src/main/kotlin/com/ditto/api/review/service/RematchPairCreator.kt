package com.ditto.api.review.service

import com.ditto.api.review.dto.EndedChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.system.OperationWeek
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 그룹 채팅이 끝날 때 참여자들의 재매칭 쌍을 만든다.
 *
 * **쌍이 없으면 그룹 평가를 제출할 수 없다.** 그룹 평가는 재매칭 의사를 필수로 받고,
 * [RematchSubmitter]가 해당 쌍을 찾지 못하면 `INVALID_REVIEW_TARGET`으로 거부한다. 그래서 평가를
 * 열기 전에 쌍이 먼저 있어야 한다 — 순서가 뒤집히면 사용자가 평가를 다 채우고 제출에서 막힌다.
 *
 * 쌍 정규화(작은 ID를 `memberId1`로)는 [Rematch.create]가 하고, 이 클래스는 "어떤 조합을 만들지"만
 * 정한다. 리뷰 서비스가 재매칭 리포지토리를 직접 알지 않게 분리한 [RematchSubmitter]와 같은 결이다.
 */
@Component
class RematchPairCreator(
    private val rematchRepository: RematchRepository,
) {
    /**
     * 참여자 전원의 비순서 쌍을 만든다. `N`명이면 `N(N-1)/2`쌍이다.
     *
     * 이미 있는 쌍은 건너뛴다(멱등) — 종료 이벤트가 재전달되거나 누락 복구가 다시 돌아도 중복되지 않는다.
     * 기존 쌍을 먼저 읽어 비교하는 이유는 유일키 위반을 예외로 받으면 참여 트랜잭션이 롤백 전용으로
     * 마킹돼, 같은 트랜잭션의 다른 작업까지 커밋되지 못하기 때문이다.
     *
     * 1:1 채팅은 재매칭 대상이 아니라 아무것도 하지 않는다.
     *
     * @return 이번 호출로 새로 만들어진 쌍 수
     */
    fun createPairsFor(endedChatRoom: EndedChatRoom): Int {
        if (endedChatRoom.matchType != ChatRoomType.GROUP) {
            return 0
        }

        val existingPairs = rematchRepository.findAllBySourceGroupMatchId(endedChatRoom.matchId)
            .map { it.memberId1 to it.memberId2 }
            .toSet()

        val newPairs = unorderedPairsOf(endedChatRoom.reviewerIds)
            .filterNot { (memberA, memberB) -> normalize(memberA, memberB) in existingPairs }
            .map { (memberA, memberB) ->
                Rematch.create(
                    sourceGroupMatchId = endedChatRoom.matchId,
                    sourceChatRoomId = endedChatRoom.chatRoomId,
                    quizSetId = endedChatRoom.quizSetId,
                    week = OperationWeek(endedChatRoom.weekStartedOn),
                    memberIdA = memberA,
                    memberIdB = memberB,
                )
            }

        rematchRepository.saveAll(newPairs)
        if (newPairs.isNotEmpty()) {
            logger.info { "재매칭 쌍 생성: chatRoomId=${endedChatRoom.chatRoomId}, ${newPairs.size}쌍" }
        }
        return newPairs.size
    }

    /** 서로 다른 두 참여자의 모든 조합. 순서는 구분하지 않으므로 (A,B)와 (B,A)를 한 번만 만든다. */
    private fun unorderedPairsOf(memberIds: List<Long>): List<Pair<Long, Long>> =
        memberIds.flatMapIndexed { index, memberA ->
            memberIds.drop(index + 1).map { memberB -> memberA to memberB }
        }

    /** 저장된 쌍은 항상 (작은 ID, 큰 ID) 순이므로 비교도 같은 순서로 맞춘다. */
    private fun normalize(memberA: Long, memberB: Long): Pair<Long, Long> =
        minOf(memberA, memberB) to maxOf(memberA, memberB)

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
