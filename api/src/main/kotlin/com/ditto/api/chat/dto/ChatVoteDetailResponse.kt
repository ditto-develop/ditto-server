package com.ditto.api.chat.dto

import com.ditto.domain.chat.entity.ChatVote
import com.ditto.domain.chat.entity.ChatVoteChoice
import com.ditto.domain.chat.entity.ChatVoteOption
import com.ditto.domain.chat.entity.ChatVoteOptionType
import com.ditto.domain.chat.entity.ChatVoteStatus
import java.time.LocalDateTime

/**
 * 투표 상세 — 생성·목록·cast·close 응답이 전부 이 형태다. 파서를 하나만 쓰게 하려는 것이고,
 * cast·close 가 갱신된 상세를 돌려줘야 FE 호출부가 재조회 없이 화면을 덮는다.
 *
 * 서버는 비율·승자를 계산하지 않는다 — 화면이 "동수면 모두 노출"이라 확정 항목이 하나로 좁혀지지
 * 않고, 1위·동표 판정은 `voterIds`와 입력 순 배열로 FE 가 한다.
 */
data class ChatVoteDetailResponse(
    val voteId: Long,
    val roomId: Long,
    val status: ChatVoteStatus,
    val allowMultiple: Boolean,
    val createdBy: Long,
    val createdAt: LocalDateTime,
    val closedAt: LocalDateTime?,
    // 분모·분자 모두 활성(이탈하지 않은) 멤버 기준 — 이탈로 분자·분모가 함께 줄어 방향이 일관된다.
    val totalMembers: Int,
    // 장소·시간 중 하나라도 표를 던진 활성 멤버 수.
    val votedCount: Int,
    val placeOptions: List<PlaceOptionResponse>,
    val timeOptions: List<TimeOptionResponse>,
    // 내가 한 표도 던지지 않았으면 null — FE 가 이 값으로 제출/결과 화면을 가른다.
    val myVote: MyVoteResponse?,
) {
    data class PlaceOptionResponse(
        val optionId: Long,
        val label: String,
        val address: String?,
        val mapLink: String?,
        val latitude: Double?,
        val longitude: Double?,
        val voterIds: List<Long>,
    )

    data class TimeOptionResponse(
        val optionId: Long,
        val meetAt: LocalDateTime,
        val voterIds: List<Long>,
    )

    data class MyVoteResponse(
        val placeIds: List<Long>,
        val timeIds: List<Long>,
    )

    companion object {
        /** 아무도 투표하기 전의 상세 — 표가 있을 수 없으므로 표 조회 없이 조립한다. `myVote`는 항상 null 이다. */
        fun beforeAnyVote(
            vote: ChatVote,
            options: List<ChatVoteOption>,
            activeMemberIds: Set<Long>,
        ): ChatVoteDetailResponse = of(
            vote = vote,
            options = options,
            choices = emptyList(),
            activeMemberIds = activeMemberIds,
            viewerId = vote.createdBy,
        )

        /**
         * @param activeMemberIds 방의 활성(이탈하지 않은) 멤버 — 분모이자 투표자 필터.
         *        이탈자의 표는 행으로 남지만 응답에서 빠진다(집계 제외 확정 정책).
         */
        fun of(
            vote: ChatVote,
            options: List<ChatVoteOption>,
            choices: List<ChatVoteChoice>,
            activeMemberIds: Set<Long>,
            viewerId: Long,
        ): ChatVoteDetailResponse {
            val activeChoices = choices.filter { it.memberId in activeMemberIds }
            val votersByOptionId = activeChoices.groupBy({ it.optionId }, { it.memberId })
            val myChoices = activeChoices.filter { it.memberId == viewerId }

            val myVote = myChoices
                .takeIf { it.isNotEmpty() }
                ?.let { mine ->
                    val optionTypeById = options.associate { it.id to it.optionType }
                    MyVoteResponse(
                        placeIds = mine.map { it.optionId }.filter { optionTypeById[it] == ChatVoteOptionType.PLACE },
                        timeIds = mine.map { it.optionId }.filter { optionTypeById[it] == ChatVoteOptionType.TIME },
                    )
                }

            return ChatVoteDetailResponse(
                voteId = vote.id,
                roomId = vote.roomId,
                status = vote.status,
                allowMultiple = vote.allowMultiple,
                createdBy = vote.createdBy,
                createdAt = vote.createdAt,
                closedAt = vote.closedAt,
                totalMembers = activeMemberIds.size,
                votedCount = activeChoices.map { it.memberId }.distinct().size,
                placeOptions = options.filter { it.optionType == ChatVoteOptionType.PLACE }.map { option ->
                    PlaceOptionResponse(
                        optionId = option.id,
                        label = checkNotNull(option.label) { "PLACE 선택지에 label 이 없습니다: optionId=${option.id}" },
                        address = option.address,
                        mapLink = option.mapLink,
                        latitude = option.latitude,
                        longitude = option.longitude,
                        voterIds = votersByOptionId[option.id].orEmpty(),
                    )
                },
                timeOptions = options.filter { it.optionType == ChatVoteOptionType.TIME }.map { option ->
                    TimeOptionResponse(
                        optionId = option.id,
                        meetAt = checkNotNull(option.meetAt) { "TIME 선택지에 meetAt 이 없습니다: optionId=${option.id}" },
                        voterIds = votersByOptionId[option.id].orEmpty(),
                    )
                },
                myVote = myVote,
            )
        }
    }
}
