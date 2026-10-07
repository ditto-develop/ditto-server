package com.ditto.api.match.matching

import com.ditto.domain.match.entity.GroupMatch

/** 그룹 인원의 하한과 상한. */
object GroupSizePolicy {

    /** 성사 최소 인원이 곧 그룹 인원의 하한이다 — 풀이 이보다 작으면 성사될 수 없는 그룹만 나온다. */
    const val MIN_SIZE = GroupMatch.ACTIVATION_THRESHOLD

    /** 그룹 최대 인원. 차단 때문에 못 담은 사람을 넣을 때도 이 이상은 채우지 않는다. */
    const val MAX_SIZE = 6
}
