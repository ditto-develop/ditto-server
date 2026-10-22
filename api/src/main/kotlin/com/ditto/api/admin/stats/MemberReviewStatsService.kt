package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.MemberReviewStatsData
import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class MemberReviewStatsService(
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
) {

    @Transactional(readOnly = true)
    fun findStats(filter: StatsFilter, week: OperationWeek, today: LocalDate): MemberReviewStatsData {
        val memberFilter = filter.toMemberFilter(today)
        val chatRoomType = filter.matchingType.chatRoomType
        val weekStartedOn = week.startedOn
        return MemberReviewStatsData(
            memberReviewCounts = memberReviewRepository.countMemberReviewsInWeek(
                weekStartedOn,
                chatRoomType,
                memberFilter,
            ),
            answerCounts = reviewAnswerRepository.countAnswersInWeek(weekStartedOn, chatRoomType, memberFilter),
        )
    }
}
