package com.ditto.api.notification.push

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.notification.entity.DeepLinkLookup
import com.ditto.domain.notification.entity.DeepLinkTarget
import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.rematch.repository.RematchRepository
import org.springframework.stereotype.Component

/**
 * 알림을 눌렀을 때 앱 웹뷰가 이동할 경로. 푸시 payload 와 알림 목록 응답이 같은 값을 쓴다.
 * FE 라우트 그대로이며 끝 슬래시 필수(FE 가 `trailingSlash: true`라 없으면 리다이렉트가 한 번 낀다).
 * 대상이 그새 지워졌으면 null 이다.
 */
@Component
class NotificationDeepLinks(
    private val chatRoomRepository: ChatRoomRepository,
    private val quizSetRepository: QuizSetRepository,
    private val rematchRepository: RematchRepository,
) {

    /** 푸시 한 건용. 대상을 그때그때 조회한다. */
    fun deepLinkOf(notification: Notification): String? = pathOf(notification, lookupById())

    /** 알림 목록 한 페이지용. 대상을 종류별로 한 번에 조회한다. 알림 ID로 찾는다. */
    fun deepLinksOf(notifications: List<Notification>): Map<Long, String?> {
        val lookup = lookupInBatch(notifications)
        return notifications.associate { it.id to pathOf(it, lookup) }
    }

    private fun pathOf(notification: Notification, lookup: TargetLookup): String? {
        val targetId = notification.targetId
        return when (notification.type.deepLinkTarget) {
            DeepLinkTarget.MATCHING_RESULT -> targetId?.let(lookup.matchingTypeOf)?.let(::matchResultPath)
            DeepLinkTarget.ONE_TO_ONE_MATCHING -> ONE_TO_ONE_MATCHING_PATH
            DeepLinkTarget.GROUP_MATCHING -> GROUP_MATCHING_PATH
            DeepLinkTarget.GROUP_CHAT_ROOM -> targetId?.let { chatRoomPath(ChatRoomType.GROUP, it) }
            DeepLinkTarget.REMATCH_CHAT_ROOM -> targetId?.let { chatRoomPath(ChatRoomType.REMATCH, it) }
            DeepLinkTarget.CHAT_ROOM -> chatRoomPathOf(targetId, lookup)
            DeepLinkTarget.CHAT_ROOM_RATING -> chatRoomPathOf(targetId, lookup)?.let { it + RATING_PATH }
            DeepLinkTarget.REMATCH_PAIR_RATING ->
                targetId?.let(lookup.sourceChatRoomIdOf)?.let { chatRoomPath(ChatRoomType.GROUP, it) + RATING_PATH }
            DeepLinkTarget.CURRENT_QUIZ -> CURRENT_QUIZ_PATH
            DeepLinkTarget.SANCTION -> SANCTION_PATH
            DeepLinkTarget.NONE -> null
        }
    }

    /** `/matching/`은 1:1 화면이라 그룹 주에 보내면 후보가 없다고 뜬다. */
    private fun matchResultPath(matchingType: MatchingType): String = when (matchingType) {
        MatchingType.GROUP -> GROUP_MATCHING_PATH
        MatchingType.ONE_TO_ONE -> ONE_TO_ONE_MATCHING_PATH
    }

    private fun chatRoomPathOf(roomId: Long?, lookup: TargetLookup): String? {
        if (roomId == null) {
            return null
        }
        val sourceType = lookup.chatRoomTypeOf(roomId) ?: return null
        return chatRoomPath(sourceType, roomId)
    }

    /** FE 방 목록과 같은 이분법이라 재매칭 방도 1:1 화면으로 연다. */
    private fun chatRoomPath(sourceType: ChatRoomType, roomId: Long): String =
        if (sourceType == ChatRoomType.GROUP) "/chat/group/$roomId/" else "/chat/one-on-one/$roomId/"

    private fun lookupById() = TargetLookup(
        chatRoomTypeOf = { roomId -> chatRoomRepository.findById(roomId).orElse(null)?.sourceType },
        matchingTypeOf = { quizSetId -> quizSetRepository.findById(quizSetId).orElse(null)?.matchingType },
        sourceChatRoomIdOf = { rematchId -> rematchRepository.findById(rematchId).orElse(null)?.sourceChatRoomId },
    )

    private fun lookupInBatch(notifications: List<Notification>): TargetLookup {
        val targetIdsByLookup = notifications
            .mapNotNull { notification ->
                notification.targetId?.let { notification.type.deepLinkTarget.lookup to it }
            }
            .groupBy({ (lookup, _) -> lookup }, { (_, targetId) -> targetId })

        val chatRoomTypeById = targetIdsByLookup[DeepLinkLookup.CHAT_ROOM]
            ?.let { ids -> chatRoomRepository.findAllById(ids.distinct()).associate { it.id to it.sourceType } }
            .orEmpty()
        val matchingTypeByQuizSetId = targetIdsByLookup[DeepLinkLookup.QUIZ_SET]
            ?.let { ids -> quizSetRepository.findAllById(ids.distinct()).associate { it.id to it.matchingType } }
            .orEmpty()
        val sourceChatRoomIdByRematchId = targetIdsByLookup[DeepLinkLookup.REMATCH]
            ?.let { ids -> rematchRepository.findAllById(ids.distinct()).associate { it.id to it.sourceChatRoomId } }
            .orEmpty()

        return TargetLookup(
            chatRoomTypeOf = chatRoomTypeById::get,
            matchingTypeOf = matchingTypeByQuizSetId::get,
            sourceChatRoomIdOf = sourceChatRoomIdByRematchId::get,
        )
    }

    private class TargetLookup(
        val chatRoomTypeOf: (Long) -> ChatRoomType?,
        val matchingTypeOf: (Long) -> MatchingType?,
        val sourceChatRoomIdOf: (Long) -> Long?,
    )

    companion object {
        private const val ONE_TO_ONE_MATCHING_PATH = "/matching/"
        private const val GROUP_MATCHING_PATH = "/matching/group/"
        private const val CURRENT_QUIZ_PATH = "/quiz/current/"
        private const val SANCTION_PATH = "/sanction/"
        private const val RATING_PATH = "rate/"
    }
}
