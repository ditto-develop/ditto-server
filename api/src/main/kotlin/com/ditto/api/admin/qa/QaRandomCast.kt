package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaVote
import com.ditto.api.chat.dto.ChatVoteCastRequest

/** 더미 일괄 투표용 무작위 선택. 단일 선택이면 장소·시간을 하나씩, 복수 선택이면 하나 이상 고른다. */
object QaRandomCast {
    fun of(vote: QaVote): ChatVoteCastRequest =
        ChatVoteCastRequest(
            placeIds = pick(vote.placeOptions.map { it.optionId }, vote.allowMultiple),
            timeIds = pick(vote.timeOptions.map { it.optionId }, vote.allowMultiple),
        )

    private fun pick(optionIds: List<Long>, allowMultiple: Boolean): List<Long> {
        val pickCount = if (allowMultiple) (1..optionIds.size).random() else 1
        return optionIds.shuffled().take(pickCount)
    }
}
