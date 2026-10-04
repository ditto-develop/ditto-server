package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.api.admin.qa.dto.QaRoomMember
import com.ditto.api.admin.qa.dto.QaRoomMessage
import com.ditto.api.admin.qa.dto.QaRoomSummary
import com.ditto.api.admin.qa.dto.QaRoomView
import com.ditto.api.admin.qa.dto.QaVote
import com.ditto.api.admin.qa.dto.QaVoteOption
import com.ditto.api.chat.dto.ChatVoteDetailResponse
import com.ditto.api.chat.service.ChatVoteService
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.entity.ChatVoteStatus
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * QA 콘솔의 채팅방 조회. 어드민은 방 멤버가 아니라서 앱의 조회 API를 쓰지 않고 저장소를 직접 읽는다.
 * 나간 사람의 커서와 이탈 표시까지 한 화면에 보여야 하기도 하다.
 */
@Service
@Transactional(readOnly = true)
class AdminQaRoomService(
    private val qaDummies: QaDummies,
    private val memberRepository: MemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatVoteService: ChatVoteService,
    private val groupMatchRepository: GroupMatchRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val quizSetRepository: QuizSetRepository,
) {
    /** 더미가 들어 있던 방. 진행 중인 방을 먼저, 그 안에서는 최근 방을 먼저 둔다. */
    fun getRoomSummaries(): List<QaRoomSummary> {
        val dummyIds = qaDummies.findIds()
        if (dummyIds.isEmpty()) return emptyList()

        val roomIds = chatRoomMemberRepository.findByMemberIdIn(dummyIds).map { it.roomId }.toSet()
        if (roomIds.isEmpty()) return emptyList()

        val rooms = chatRoomRepository.findAllById(roomIds)
            .sortedWith(compareBy<ChatRoom> { it.isEnded }.thenByDescending { it.id })
        val activeMembersByRoomId = chatRoomMemberRepository.findByRoomIdIn(roomIds)
            .filterNot { it.hasLeft }
            .groupBy { it.roomId }
        val everyActiveMemberId = activeMembersByRoomId.values.flatten().map { it.memberId }
        val members = QaMembers(memberRepository.findAllById(everyActiveMemberId))
        val sourceLabels = composeSourceLabels(rooms)

        return rooms.map { room ->
            val activeMemberIds = activeMembersByRoomId[room.id].orEmpty().map { it.memberId }
            QaRoomSummary(
                roomId = room.id,
                sourceType = room.sourceType,
                sourceLabel = sourceLabels[room.id],
                status = room.status,
                opensAt = room.opensAt,
                expiresAt = room.expiresAt,
                realMembers = activeMemberIds.filterNot { it in dummyIds }.map(members::of),
                dummyCount = activeMemberIds.count { it in dummyIds },
                lastMessageAt = chatMessageRepository.findFirstByRoomIdOrderByIdDesc(room.id)?.createdAt,
            )
        }
    }

    /** 최근 메시지 [TIMELINE_SIZE]개를 오래된 순서로 준다. 방이 없으면 null. */
    fun findRoom(roomId: Long): QaRoomView? {
        val room = chatRoomRepository.findByIdOrNull(roomId) ?: return null
        val dummyIds = qaDummies.findIds()
        val roomMembers = chatRoomMemberRepository.findByRoomId(roomId)
        val messages = chatMessageRepository.findByRoomIdWithCursor(roomId, cursor = null, size = TIMELINE_SIZE)
            .reversed()
        val memberIds = roomMembers.map { it.memberId } + messages.map { it.senderId }
        val members = QaMembers(memberRepository.findAllById(memberIds))
        val votes = findVotes(room, roomMembers)

        return QaRoomView(
            roomId = room.id,
            sourceType = room.sourceType,
            status = room.status,
            opensAt = room.opensAt,
            expiresAt = room.expiresAt,
            endReason = room.endReason,
            members = roomMembers
                .sortedWith(compareBy({ it.memberId in dummyIds }, { it.memberId }))
                .map { roomMember ->
                    QaRoomMember(
                        member = members.of(roomMember.memberId),
                        dummy = roomMember.memberId in dummyIds,
                        left = roomMember.hasLeft,
                        lastReadMessageId = roomMember.lastReadMessageId,
                    )
                },
            messages = messages.map { message ->
                QaRoomMessage(
                    messageId = message.id,
                    sender = members.of(message.senderId),
                    fromDummy = message.senderId in dummyIds,
                    messageType = message.messageType,
                    content = message.content,
                    sentAt = message.createdAt,
                    unreadCount = message.unreadCountAmong(roomMembers),
                )
            },
            votes = votes.map { it.toQaVote(members) },
        )
    }

    fun findVote(roomId: Long, voteId: Long): QaVote? {
        val room = chatRoomRepository.findByIdOrNull(roomId) ?: return null
        val roomMembers = chatRoomMemberRepository.findByRoomId(roomId)
        val vote = findVotes(room, roomMembers).firstOrNull { it.voteId == voteId } ?: return null
        return vote.toQaVote(QaMembers(memberRepository.findAllById(roomMembers.map { it.memberId })))
    }

    fun findLatestMessageId(roomId: Long): Long? = chatMessageRepository.findFirstByRoomIdOrderByIdDesc(roomId)?.id

    fun findActiveDummiesIn(roomId: Long): List<QaMember> {
        val dummyIds = qaDummies.findIds()
        val activeDummyIds = chatRoomMemberRepository.findByRoomId(roomId)
            .filter { !it.hasLeft && it.memberId in dummyIds }
            .map { it.memberId }
        val members = QaMembers(memberRepository.findAllById(activeDummyIds))
        return activeDummyIds.map(members::of)
    }

    /** 그룹 방은 그룹 번호와 퀴즈셋, 1:1 방은 퀴즈셋, 재매칭 방은 쌍 번호. 화면에서 테스트한 방을 찾는 단서다. */
    private fun composeSourceLabels(rooms: List<ChatRoom>): Map<Long, String> {
        val quizSetIdByGroupMatchId = groupMatchRepository
            .findAllById(rooms.filter { it.sourceType == ChatRoomType.GROUP }.map { it.sourceId })
            .associate { it.id to it.quizSetId }
        val quizSetIdByPersonalMatchId = personalMatchRepository
            .findAllById(rooms.filter { it.sourceType == ChatRoomType.PERSONAL }.map { it.sourceId })
            .associate { it.id to it.quizSetId }
        val quizSetTitles = quizSetRepository
            .findAllById(quizSetIdByGroupMatchId.values + quizSetIdByPersonalMatchId.values)
            .associate { it.id to it.title }

        return rooms.mapNotNull { room ->
            val label = when (room.sourceType) {
                ChatRoomType.GROUP ->
                    quizSetIdByGroupMatchId[room.sourceId]?.let { "그룹 #${room.sourceId} · ${quizSetTitles[it]}" }
                ChatRoomType.PERSONAL -> quizSetIdByPersonalMatchId[room.sourceId]?.let { quizSetTitles[it] }
                ChatRoomType.REMATCH -> "재매칭 #${room.sourceId}"
            }
            label?.let { room.id to it }
        }.toMap()
    }

    /**
     * 앱 조회는 나가지 않은 방 멤버만 되므로 그중 한 명의 눈으로 읽는다. 집계(voterIds)는 보는 사람과 무관하다.
     * 모두 나간 방은 앱에서도 투표를 볼 사람이 없어 비워 둔다.
     */
    private fun findVotes(room: ChatRoom, roomMembers: List<ChatRoomMember>): List<ChatVoteDetailResponse> {
        if (room.sourceType != ChatRoomType.GROUP) return emptyList()
        val viewerId = roomMembers.firstOrNull { !it.hasLeft }?.memberId ?: return emptyList()
        return chatVoteService.getVotes(room.id, viewerId)
    }

    private fun ChatVoteDetailResponse.toQaVote(members: QaMembers): QaVote =
        QaVote(
            voteId = voteId,
            open = status == ChatVoteStatus.OPEN,
            allowMultiple = allowMultiple,
            votedCount = votedCount,
            totalMembers = totalMembers,
            closedReason = closedReason,
            placeOptions = placeOptions.map { QaVoteOption(it.optionId, it.label, it.voterIds.map(members::of)) },
            timeOptions = timeOptions.map {
                QaVoteOption(it.optionId, MEET_AT_FORMAT.format(it.meetAt), it.voterIds.map(members::of))
            },
        )

    companion object {
        const val TIMELINE_SIZE = 50
        private val MEET_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd(E) HH:mm", Locale.KOREAN)
    }
}
