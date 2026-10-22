package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.collections.shouldContainExactly
import java.time.LocalDate
import javax.sql.DataSource

private val TODAY = LocalDate.of(2026, 10, 22)
private val WEEK = OperationWeek.containing(TODAY)

class MemberReviewStatsServiceTest(
    private val memberReviewStatsService: MemberReviewStatsService,
    private val memberReviewRepository: MemberReviewRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "평가 원본" - {
        "매칭 유형 그룹을 고르면 그룹 채팅방 평가지만 센다" {
            val authorId = memberRepository.save(MemberFixture.create()).id
            listOf(ChatRoomType.PERSONAL, ChatRoomType.GROUP).forEachIndexed { index, matchType ->
                memberReviewRepository.save(
                    MemberReviewFixture.create(
                        authorMemberId = authorId,
                        matchType = matchType,
                        chatRoomId = index + 1L,
                        weekStartedOn = WEEK.startedOn,
                    ),
                )
            }

            val groupOnly = StatsFilter(matchingType = MatchingTypeFilter.GROUP)

            val stats = memberReviewStatsService.findStats(groupOnly, WEEK, TODAY)

            stats.memberReviewCounts.map { it.matchType } shouldContainExactly listOf(ChatRoomType.GROUP)
        }
    }
})
