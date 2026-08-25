package com.ditto.domain.chat.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

/**
 * 투표에 던진 표 — 제시된 선택지([ChatVoteOption]) 가운데 회원이 고른 것 하나가 한 행이다.
 *
 * 카운트가 아니라 행으로 남기는 이유: 결과 화면이 "누가 무엇에 투표했는지"를 실명으로 보여준다
 * (피그마 4.2.4 — "2명 · 와그작, 댕이누나"). 재투표는 그 회원의 표를 지우고 다시 넣는 치환이라
 * 갱신 명령이 없고, 행은 만들어진 뒤 바뀌지 않는다.
 *
 * [voteId]를 중복 보관하는 이유: 내 표 조회·votedCount 집계·치환 삭제가 전부 투표 단위라,
 * 선택지를 거쳐 조인하는 대신 이 컬럼 하나로 읽는다.
 */
@Entity
@Table(
    name = "chat_vote_choice",
    uniqueConstraints = [
        UniqueConstraint(name = "chat_vote_choice_uk_1", columnNames = ["option_id", "member_id"]),
    ],
    indexes = [
        // 상세 조회(집계 + 내 표)와 재투표 치환이 한 투표의 표를 한 번에 읽는 경로.
        Index(name = "chat_vote_choice_index_1", columnList = "vote_id, member_id"),
    ],
)
class ChatVoteChoice private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("투표 ID (선택지를 거치지 않고 내 표·집계를 읽으려고 중복 보관)")
    @Column(name = "vote_id", nullable = false)
    val voteId: Long,

    @Comment("선택지 ID")
    @Column(name = "option_id", nullable = false)
    val optionId: Long,

    @Comment("투표한 회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
) : BaseEntity() {

    companion object {
        fun of(voteId: Long, optionId: Long, memberId: Long): ChatVoteChoice =
            ChatVoteChoice(voteId = voteId, optionId = optionId, memberId = memberId)
    }
}
