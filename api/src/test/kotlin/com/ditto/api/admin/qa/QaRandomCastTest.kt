package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaVote
import com.ditto.api.admin.qa.dto.QaVoteOption
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty

class QaRandomCastTest : FreeSpec({

    fun vote(allowMultiple: Boolean) = QaVote(
        voteId = 1L,
        isOpen = true,
        allowMultiple = allowMultiple,
        votedCount = 0,
        totalMembers = 3,
        closedReason = null,
        placeOptions = listOf(10L, 11L, 12L).map { QaVoteOption(it, "장소$it", emptyList()) },
        timeOptions = listOf(20L, 21L).map { QaVoteOption(it, "시간$it", emptyList()) },
    )

    "단일 선택 투표는 장소·시간을 하나씩 고른다" {
        repeat(20) {
            val cast = QaRandomCast.of(vote(allowMultiple = false))
            cast.placeIds shouldHaveSize 1
            cast.timeIds shouldHaveSize 1
            cast.placeIds.single() shouldBeIn listOf(10L, 11L, 12L)
        }
    }

    "복수 선택 투표는 각 유형에서 하나 이상을 중복 없이 고른다" {
        repeat(20) {
            val cast = QaRandomCast.of(vote(allowMultiple = true))
            cast.placeIds.shouldNotBeEmpty()
            cast.timeIds.shouldNotBeEmpty()
            cast.placeIds.toSet() shouldHaveSize cast.placeIds.size
        }
    }
})
