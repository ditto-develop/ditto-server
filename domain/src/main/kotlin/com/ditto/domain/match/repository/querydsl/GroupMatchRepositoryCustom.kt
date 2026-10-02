package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.GroupMatch
import java.time.LocalDate

interface GroupMatchRepositoryCustom {

    /**
     * 응답 마감이 지났는데도 성사되지 못한 그룹들. 운영 주(월요일) 기준으로 구간을 받는다.
     *
     * @param oldestWeekStartedOn 포함할 가장 오래된 운영 주. 안내는 마감 직후의 것이라 지난 주차를
     *   무한정 다시 집으면 안 된다 — 알림 행이 purge 된 뒤 같은 안내가 다시 나간다.
     * @param newestWeekStartedOn 포함할 가장 늦은 운영 주. 응답 마감이 지난 가장 최근 주를 넘긴다.
     */
    fun findUnformedBetween(oldestWeekStartedOn: LocalDate, newestWeekStartedOn: LocalDate): List<GroupMatch>

    /** 그 주 퀴즈셋의 그룹 ID 전부. 성사 여부는 가리지 않는다. */
    fun findGroupMatchIdsByWeekStartedOn(weekStartedOn: LocalDate): List<Long>
}
