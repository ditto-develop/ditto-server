package com.ditto.api.chat.service

import com.ditto.api.chat.dto.ChatVoteCastRequest
import com.ditto.api.chat.dto.ChatVoteCreateRequest
import com.ditto.api.chat.dto.ChatVoteDetailResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.entity.ChatVote
import com.ditto.domain.chat.entity.ChatVoteChoice
import com.ditto.domain.chat.entity.ChatVoteOption
import com.ditto.domain.chat.entity.ChatVoteOptionType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.chat.repository.ChatVoteChoiceRepository
import com.ditto.domain.chat.repository.ChatVoteOptionRepository
import com.ditto.domain.chat.repository.ChatVoteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 그룹 만남 투표(피그마 4.2.2~4.2.4) — 생성과 조회.
 *
 * 잠금 순서는 방 → 멤버 → 투표로 고정한다(그룹 이탈 트랜잭션이 방 → 멤버를 이미 고정했다).
 * 생성은 방 행 잠금이 트랜잭션의 **첫 접근**이어야 한다(ADR 0011 규칙 5) — 그래서
 * `ChatRoomAccessChecker`를 쓰지 않고 잠근 엔티티로 직접 판정한다(그 안의 비잠금 `findById`가
 * 영속성 컨텍스트에 낡은 방 인스턴스를 먼저 올린다).
 */
@Service
@Transactional(readOnly = true)
class ChatVoteService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatVoteRepository: ChatVoteRepository,
    private val chatVoteOptionRepository: ChatVoteOptionRepository,
    private val chatVoteChoiceRepository: ChatVoteChoiceRepository,
    private val chatRoomAccessChecker: ChatRoomAccessChecker,
) {

    /**
     * 투표를 만든다. 방당 열린 투표는 하나다 — 방 행 잠금이 동시 생성을 직렬화하고,
     * 잠금을 빠뜨린 경로가 생겨도 `chat_vote_uk_1`(open_room_id 유일)이 마지막으로 막는다.
     *
     * 생성은 멱등이 아니다. 이미 열린 투표가 있으면 그것을 돌려주지 않고 `VOTE_ALREADY_EXISTS`로
     * 거부한다 — 돌려주면 FE 가 자기가 만든 투표라고 착각한다.
     */
    @Transactional
    fun createVote(roomId: Long, memberId: Long, request: ChatVoteCreateRequest): ChatVoteDetailResponse {
        val room = chatRoomRepository.findWithLockById(roomId) ?: throw WarnException(ErrorCode.CHAT_ROOM_NOT_FOUND)

        validateVotableRoom(room, memberId)
        validateNoDuplicateOptions(request)

        if (chatVoteRepository.findByOpenRoomId(roomId) != null) {
            throw WarnException(ErrorCode.VOTE_ALREADY_EXISTS)
        }

        val vote = chatVoteRepository.save(
            ChatVote.open(roomId = roomId, createdBy = memberId, allowMultiple = request.allowMultiple),
        )
        val options = chatVoteOptionRepository.saveAll(
            request.placeOptions.map {
                ChatVoteOption.createPlaceOption(
                    voteId = vote.id,
                    createdBy = memberId,
                    label = it.label.trim(),
                    address = it.address,
                    mapLink = it.mapLink,
                    latitude = it.latitude,
                    longitude = it.longitude,
                )
            } + request.timeOptions.map {
                ChatVoteOption.createTimeOption(voteId = vote.id, createdBy = memberId, meetAt = it.meetAt)
            },
        )

        return ChatVoteDetailResponse.beforeAnyVote(
            vote = vote,
            options = options,
            activeMemberIds = activeMemberIds(roomId),
        )
    }

    /**
     * 방의 투표 목록 — 최신이 앞이다. 재접속·브로드캐스트 유실 시 배너 복구의 근거다.
     * 조회는 종료된 방·이탈자에게도 열려 있다(지난 결과를 봐야 한다 — 읽기 전용 규칙).
     */
    fun getVotes(roomId: Long, memberId: Long): List<ChatVoteDetailResponse> {
        chatRoomAccessChecker.validateMember(roomId, memberId)
        val activeMemberIds = activeMemberIds(roomId)
        return chatVoteRepository.findAllByRoomIdOrderByIdDesc(roomId).map { vote ->
            toDetail(vote, activeMemberIds, memberId)
        }
    }

    /** 투표 상세 — 집계와 내 표. */
    fun getVote(roomId: Long, voteId: Long, memberId: Long): ChatVoteDetailResponse {
        chatRoomAccessChecker.validateMember(roomId, memberId)
        val vote = findVoteInRoom(voteId, roomId)
        return toDetail(vote, activeMemberIds(roomId), memberId)
    }

    /**
     * 투표하기 — 요청에 담긴 집합이 그 회원의 최종 선택이다(치환).
     *
     * 투표 행 잠금이 트랜잭션의 첫 접근이다(ADR 0011 규칙 5). 동시 cast 는 이 잠금이 직렬화한다 —
     * 잠그지 않으면 삭제와 삽입 사이의 중간 상태(표가 줄어든 순간)를 다른 조회가 본다.
     * 방 행은 잠그지 않는다(수정하지 않고, 상태 판정은 잠금 획득 뒤에 읽어 최신 커밋을 본다).
     *
     * 치환은 차집합이다 — 빠진 표만 지우고 새 표만 넣고 겹치는 표는 건드리지 않는다.
     * 전량 삭제 후 재삽입은 Hibernate flush 가 INSERT 를 DELETE 보다 먼저 실행해
     * 겹치는 표에서 chat_vote_choice_uk_1 에 걸린다(설계서 §5-4). 재투표 화면이 기존 선택을
     * 유지한 채 재제출하므로 겹침이 정상 경로다.
     */
    @Transactional
    fun cast(roomId: Long, voteId: Long, memberId: Long, request: ChatVoteCastRequest): ChatVoteDetailResponse {
        val vote = chatVoteRepository.findWithLockById(voteId) ?: throw WarnException(ErrorCode.VOTE_NOT_FOUND)
        if (vote.roomId != roomId) {
            throw WarnException(ErrorCode.VOTE_NOT_FOUND)
        }
        val room = chatRoomRepository.findById(roomId).orElseThrow {
            WarnException(ErrorCode.CHAT_ROOM_NOT_FOUND)
        }

        validateVotableRoom(room, memberId)

        if (vote.isClosed) {
            throw WarnException(ErrorCode.VOTE_ALREADY_CLOSED)
        }

        val options = chatVoteOptionRepository.findAllByVoteIdOrderByIdAsc(voteId)
        val wantedOptionIds = validateCastSelection(vote, options, request)

        val choices = chatVoteChoiceRepository.findAllByVoteId(voteId)
        val myChoices = choices.filter { it.memberId == memberId }
        val currentOptionIds = myChoices.map { it.optionId }.toSet()

        chatVoteChoiceRepository.deleteAllByIdInBatch(
            myChoices.filter { it.optionId !in wantedOptionIds }.map { it.id },
        )
        val inserted = chatVoteChoiceRepository.saveAll(
            (wantedOptionIds - currentOptionIds).map { ChatVoteChoice.of(voteId, it, memberId) },
        )

        val untouched = myChoices.filter { it.optionId in wantedOptionIds }
        val othersChoices = choices.filter { it.memberId != memberId }
        return ChatVoteDetailResponse.of(
            vote = vote,
            options = options,
            choices = othersChoices + untouched + inserted,
            activeMemberIds = activeMemberIds(roomId),
            viewerId = memberId,
        )
    }

    /**
     * 선택이 이 투표에서 유효한지 — 전부 이 투표의 선택지이고 유형이 맞아야 하며,
     * 복수 선택이 꺼져 있으면 유형별 1개까지다. 같은 ID 중복은 오류가 아니라 한 표로 접는다.
     *
     * @return 최종 선택 집합 (치환의 목표 상태)
     */
    private fun validateCastSelection(
        vote: ChatVote,
        options: List<ChatVoteOption>,
        request: ChatVoteCastRequest,
    ): Set<Long> {
        val placeIds = request.placeIds.distinct()
        val timeIds = request.timeIds.distinct()

        val optionTypeById = options.associate { it.id to it.optionType }
        val typeMismatched = placeIds.any { optionTypeById[it] != ChatVoteOptionType.PLACE } ||
            timeIds.any { optionTypeById[it] != ChatVoteOptionType.TIME }
        if (typeMismatched) {
            throw WarnException(ErrorCode.INVALID_VOTE_OPTION)
        }
        if (!vote.allowMultiple && (placeIds.size > 1 || timeIds.size > 1)) {
            throw WarnException(ErrorCode.VOTE_MULTIPLE_NOT_ALLOWED)
        }
        return (placeIds + timeIds).toSet()
    }

    /**
     * 투표를 던질 수 있는 방인지 — 그룹이고, 열려 있고, 내가 아직 나가지 않은 멤버여야 한다.
     *
     * 이탈 판정을 exists 가 아니라 행 조회 + [hasLeft]로 하는 이유: 이탈은 행 삭제가 아니라
     * left_at 소프트 컬럼이라 exists 는 나간 멤버에게도 참이다.
     */
    private fun validateVotableRoom(room: ChatRoom, memberId: Long) {
        if (room.sourceType != ChatRoomType.GROUP) {
            throw WarnException(ErrorCode.GROUP_ROOM_ONLY)
        }
        val roomMember = chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, memberId)
            ?: throw chatRoomAccessChecker.notFoundOrForbidden(room.id)

        if (roomMember.hasLeft) {
            throw WarnException(ErrorCode.NOT_CHAT_ROOM_MEMBER, "이미 나간 채팅방입니다.")
        }
        if (room.isBeforeOpen) {
            throw WarnException(ErrorCode.CHAT_ROOM_NOT_OPENED)
        }
        if (room.isEnded) {
            throw WarnException(ErrorCode.CHAT_ROOM_ENDED)
        }
    }

    /**
     * 요청 안의 중복을 저장 전에 거른다 — DB 유일키(uk_1·uk_2)가 최종으로 막지만,
     * 그때는 이미 INSERT 라 INTERNAL_ERROR 로 새므로 여기서 8205 로 정확히 답한다.
     */
    private fun validateNoDuplicateOptions(request: ChatVoteCreateRequest) {
        val labels = request.placeOptions.map { it.label.trim() }
        if (labels.size != labels.distinct().size) {
            throw WarnException(ErrorCode.DUPLICATE_VOTE_OPTION)
        }
        val meetAts = request.timeOptions.map { it.meetAt.withSecond(0).withNano(0) }
        if (meetAts.size != meetAts.distinct().size) {
            throw WarnException(ErrorCode.DUPLICATE_VOTE_OPTION)
        }
    }

    private fun findVoteInRoom(voteId: Long, roomId: Long): ChatVote {
        val vote = chatVoteRepository.findById(voteId).orElse(null)
            ?: throw WarnException(ErrorCode.VOTE_NOT_FOUND)
        // 다른 방의 투표 ID 로 접근하면 존재를 숨기지 않고 404 로 답한다 — 방 멤버십은 이미 검증됐다.
        if (vote.roomId != roomId) {
            throw WarnException(ErrorCode.VOTE_NOT_FOUND)
        }
        return vote
    }

    private fun toDetail(vote: ChatVote, activeMemberIds: Set<Long>, viewerId: Long): ChatVoteDetailResponse =
        ChatVoteDetailResponse.of(
            vote = vote,
            options = chatVoteOptionRepository.findAllByVoteIdOrderByIdAsc(vote.id),
            choices = chatVoteChoiceRepository.findAllByVoteId(vote.id),
            activeMemberIds = activeMemberIds,
            viewerId = viewerId,
        )

    private fun activeMemberIds(roomId: Long): Set<Long> =
        chatRoomMemberRepository.findByRoomIdIn(listOf(roomId))
            .filter { !it.hasLeft }
            .map { it.memberId }
            .toSet()
}
