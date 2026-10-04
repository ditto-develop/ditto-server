package com.ditto.api.admin.qa

import com.ditto.api.chat.dto.ChatVoteCreateRequest
import com.ditto.api.chat.dto.ChatVoteCreateRequest.PlaceOptionRequest
import com.ditto.api.chat.dto.ChatVoteCreateRequest.TimeOptionRequest
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** QA 콘솔이 더미로 만드는 투표. 장소 3개와 [now] 다음 토요일의 두 시각을 선택지로 둔다. */
object QaSampleVote {
    private val PLACES = listOf("성수 카페 어니언", "강남역 보드게임 카페", "홍대 전시관")
    private val MEET_HOURS = listOf(14, 18)

    fun request(allowMultiple: Boolean, now: LocalDateTime): ChatVoteCreateRequest {
        val nextSaturday = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        return ChatVoteCreateRequest(
            allowMultiple = allowMultiple,
            placeOptions = PLACES.map { PlaceOptionRequest(label = it) },
            timeOptions = MEET_HOURS.map { TimeOptionRequest(meetAt = nextSaturday.atTime(it, 0)) },
        )
    }
}
