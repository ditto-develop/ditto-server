package com.ditto.domain.chat.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

/**
 * 그룹 채팅방에서 만날 장소·시간을 정하는 투표(피그마 4.2.2~4.2.4).
 *
 * **방당 열린 투표는 하나다.** 화면이 "투표 생성 후 바텀시트에서 '투표 만들기'를 지운다"로 그 규칙을
 * 못박았다. 그 하나를 [openRoomId]가 지킨다 — 열려 있는 동안만 `roomId` 사본을 들고 있고 마감하며
 * 비우므로, 유일 제약이 NULL 중복을 허용하는 성질을 그대로 이용해 "닫힌 투표는 몇 개든, 열린 투표는
 * 방당 하나"가 된다. 앱에서도 잠금으로 막지만 그 검사를 빠뜨린 경로가 생겨도 DB 가 마지막으로 막는다.
 *
 * 승자는 여기 저장하지 않는다. 화면이 "동수면 해당 항목을 모두 노출"이라 확정 항목이 하나로 좁혀지지
 * 않고, 서버가 승자를 고르면 그 규칙 자체가 계약이 되어 버린다. 서버는 표를 그대로 내고 1위·동표
 * 판정은 화면이 한다.
 */
@Entity
@Table(
    name = "chat_vote",
    uniqueConstraints = [
        UniqueConstraint(name = "chat_vote_uk_1", columnNames = ["open_room_id"]),
    ],
    indexes = [
        // 방의 투표를 최신순으로 읽는 경로 (방 진입·재접속 복구).
        Index(name = "chat_vote_index_1", columnList = "room_id, id"),
    ],
)
class ChatVote private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("채팅방 ID")
    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    @Comment("투표를 만든 회원 ID")
    @Column(name = "created_by", nullable = false)
    val createdBy: Long,

    @Comment("복수 선택 허용 여부 (생성 시 확정, 장소·시간 공통)")
    @Column(name = "allow_multiple", nullable = false)
    val allowMultiple: Boolean,

    openRoomId: Long?,
) : BaseEntity() {

    @Comment("열려 있는 동안만 room_id 사본. 마감하면 NULL (방당 열린 투표 1개 제약용)")
    @Column(name = "open_room_id")
    var openRoomId: Long? = openRoomId
        protected set

    @Comment("상태 (OPEN, CLOSED)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ChatVoteStatus = ChatVoteStatus.OPEN
        protected set

    @Comment("마감 시각 (진행 중이면 NULL)")
    @Column(name = "closed_at")
    var closedAt: LocalDateTime? = null
        protected set

    @Comment("마감한 회원 ID (진행 중이면 NULL)")
    @Column(name = "closed_by")
    var closedBy: Long? = null
        protected set

    val isClosed: Boolean
        get() = status == ChatVoteStatus.CLOSED

    /**
     * 투표를 마감한다. 마감하면 표를 던질 수 없고 결과만 읽는다.
     *
     * 이미 마감된 투표를 다시 마감하는 것은 호출자가 [isClosed] 확인을 빠뜨린 것이므로 조용히 넘기지
     * 않는다 — 최초 마감 시각·마감자가 덮이면 "언제 누가 닫았는지"가 사라진다.
     * (재요청을 성공으로 답하는 멱등 처리는 서비스가 [isClosed]를 먼저 보고 한다 — 채팅방 종료와 같은 결)
     *
     * [openRoomId]를 함께 비워 방당 1개 제약에서 풀어 준다. 그래야 같은 방에 다음 투표를 만들 수 있다.
     */
    fun close(by: Long, at: LocalDateTime) {
        check(!isClosed) { "이미 마감된 투표입니다: id=$id, closedAt=$closedAt" }
        status = ChatVoteStatus.CLOSED
        closedAt = at
        closedBy = by
        openRoomId = null
    }

    companion object {
        fun open(roomId: Long, createdBy: Long, allowMultiple: Boolean): ChatVote =
            ChatVote(
                roomId = roomId,
                createdBy = createdBy,
                allowMultiple = allowMultiple,
                // 열린 채로 태어나므로 제약 대상이다. 마감이 이 값을 비운다.
                openRoomId = roomId,
            )
    }
}
