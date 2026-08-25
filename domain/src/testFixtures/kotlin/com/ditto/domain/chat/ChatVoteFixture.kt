package com.ditto.domain.chat

import com.ditto.domain.chat.entity.ChatVote
import com.ditto.domain.chat.entity.ChatVoteChoice
import com.ditto.domain.chat.entity.ChatVoteOption
import com.ditto.domain.withId
import java.time.LocalDateTime

object ChatVoteFixture {

    fun open(
        roomId: Long = 1L,
        createdBy: Long = 1L,
        allowMultiple: Boolean = false,
        id: Long = 0L,
    ): ChatVote = ChatVote.open(
        roomId = roomId,
        createdBy = createdBy,
        allowMultiple = allowMultiple,
    ).withId(id)

    fun place(
        voteId: Long = 1L,
        createdBy: Long = 1L,
        label: String = "성수 카페거리",
        address: String? = "서울 성동구 성수동2가",
        mapLink: String? = "http://place.map.kakao.com/26338954",
        latitude: Double? = 37.5446,
        longitude: Double? = 127.0559,
        id: Long = 0L,
    ): ChatVoteOption = ChatVoteOption.createPlaceOption(
        voteId = voteId,
        createdBy = createdBy,
        label = label,
        address = address,
        mapLink = mapLink,
        latitude = latitude,
        longitude = longitude,
    ).withId(id)

    fun time(
        voteId: Long = 1L,
        createdBy: Long = 1L,
        meetAt: LocalDateTime = LocalDateTime.of(2026, 8, 29, 19, 0),
        id: Long = 0L,
    ): ChatVoteOption = ChatVoteOption.createTimeOption(
        voteId = voteId,
        createdBy = createdBy,
        meetAt = meetAt,
    ).withId(id)

    fun choice(
        voteId: Long = 1L,
        optionId: Long = 1L,
        memberId: Long = 1L,
        id: Long = 0L,
    ): ChatVoteChoice = ChatVoteChoice.of(
        voteId = voteId,
        optionId = optionId,
        memberId = memberId,
    ).withId(id)
}
