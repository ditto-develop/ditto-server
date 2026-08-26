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
 * 투표 선택지. PLACE·TIME 이 유형별 전용 필드를 갖고 한 테이블에 산다 —
 * 상한(유형별 10개)·중복 금지·입력 순 정렬·집계가 전부 유형 무관이라 로직을 한 벌로 쓰기 위해서다.
 *
 * 유형별 필수 필드는 컬럼 NOT NULL 로 지킬 수 없으므로 생성 경로를 [createPlaceOption]·[createTimeOption] 팩토리로만 연다.
 * 중복 판정은 DB 유일 제약이 최종으로 막는다 — PLACE 는 `(vote_id, option_type, label)`,
 * TIME 은 표시 문구가 아니라 실제 일시 `(vote_id, meet_at)` 로 본다.
 *
 * 입력 순 정렬 컬럼은 따로 두지 않는다. `id` 오름차순이 곧 입력 순이고,
 * 동표일 때 "입력 순으로 노출"하는 화면 규칙(피그마 4.2.4)이 그 순서를 그대로 쓴다.
 */
@Entity
@Table(
    name = "chat_vote_option",
    uniqueConstraints = [
        UniqueConstraint(name = "chat_vote_option_uk_1", columnNames = ["vote_id", "option_type", "label"]),
        UniqueConstraint(name = "chat_vote_option_uk_2", columnNames = ["vote_id", "meet_at"]),
    ],
    indexes = [
        // 상세·결과 조회가 선택지를 유형별로 입력 순으로 읽는 경로.
        Index(name = "chat_vote_option_index_1", columnList = "vote_id, option_type, id"),
    ],
)
class ChatVoteOption private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("투표 ID")
    @Column(name = "vote_id", nullable = false)
    val voteId: Long,

    @Comment("선택지 유형 (PLACE, TIME)")
    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false, length = 20)
    val optionType: ChatVoteOptionType,

    @Comment("PLACE 상호명 (TIME 은 NULL — 표시 문구는 meet_at 으로 클라이언트가 만든다)")
    @Column(length = LABEL_MAX_LENGTH)
    val label: String?,

    @Comment("PLACE 도로명 주소")
    @Column(length = ADDRESS_MAX_LENGTH)
    val address: String?,

    @Comment("PLACE 카카오맵 URL")
    @Column(name = "map_link", length = MAP_LINK_MAX_LENGTH)
    val mapLink: String?,

    @Comment("PLACE 위도")
    @Column
    val latitude: Double?,

    @Comment("PLACE 경도")
    @Column
    val longitude: Double?,

    @Comment("TIME 만날 일시 (분 단위, 초 이하 0)")
    @Column(name = "meet_at")
    val meetAt: LocalDateTime?,

    @Comment("선택지를 만든 회원 ID")
    @Column(name = "created_by", nullable = false)
    val createdBy: Long,
) : BaseEntity() {

    /** 같은 상호명인지 — [normalizeLabel] 기준. TIME 선택지는 label 이 없어 항상 false 다. */
    fun hasSameLabel(candidate: String): Boolean =
        label?.let { normalizeLabel(it) } == normalizeLabel(candidate)

    companion object {
        /**
         * 상호명 중복 판정의 단일 기준 — 앞뒤 공백을 걷고 소문자로 접는다.
         * 생성(요청 내 중복)과 추가(기존 행과 중복)가 반드시 같은 함수를 써야 한다 —
         * lowercase()와 equals(ignoreCase)는 일부 문자(터키어 İ 등)에서 판정이 갈린다.
         */
        fun normalizeLabel(label: String): String = label.trim().lowercase()

        /** 유형별 선택지 상한 — 생성(2~10)과 진행 중 추가가 함께 지키는 도메인 규칙이다. */
        const val MAX_COUNT_PER_TYPE = 10

        const val LABEL_MAX_LENGTH = 100
        const val ADDRESS_MAX_LENGTH = 200
        const val MAP_LINK_MAX_LENGTH = 500

        fun createPlaceOption(
            voteId: Long,
            createdBy: Long,
            label: String,
            address: String?,
            mapLink: String?,
            latitude: Double?,
            longitude: Double?,
        ): ChatVoteOption = ChatVoteOption(
            voteId = voteId,
            optionType = ChatVoteOptionType.PLACE,
            // trim 을 여기서 하는 이유는 초 절삭과 같다 — 저장 규칙을 호출부의 기억력에 맡기지 않는다.
            label = label.trim(),
            address = address,
            mapLink = mapLink,
            latitude = latitude,
            longitude = longitude,
            meetAt = null,
            createdBy = createdBy,
        )

        /** 초 이하를 버리는 이유: 중복 판정이 meet_at 유일 제약이라 같은 분(分)은 같은 시간이어야 한다. */
        fun createTimeOption(voteId: Long, createdBy: Long, meetAt: LocalDateTime): ChatVoteOption = ChatVoteOption(
            voteId = voteId,
            optionType = ChatVoteOptionType.TIME,
            label = null,
            address = null,
            mapLink = null,
            latitude = null,
            longitude = null,
            meetAt = meetAt.withSecond(0).withNano(0),
            createdBy = createdBy,
        )
    }
}
