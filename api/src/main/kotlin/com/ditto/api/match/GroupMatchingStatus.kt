package com.ditto.api.match

import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime

/** 회원이 그 주 그룹 셋에서 어디쯤 있는지. 매칭 현황 API 의 그룹 플래그와 그룹 완주자의 1:1 응시 판단이 같이 쓴다. */
enum class GroupMatchingStatus {
    /** 그 셋에 후보 그룹이 하나도 없다 */
    NO_CANDIDATE_GROUPS,

    /** 셋에 후보 그룹은 있는데 내 그룹이 없다 */
    NOT_INVITED,

    DECLINED,

    NOT_RESPONDED,

    /** 수락했고 인원이 차기를 기다린다 */
    WAITING_FOR_GROUP,

    /** 수락한 그룹이 성사됐다. 채팅방을 나갔어도 그렇다 */
    JOINED_GROUP,
    ;

    // 그룹 응답을 기다리는 동안은 그룹이 성사될 수 있어서 그룹 응답 마감까지 1:1 을 막는다.
    // 미응답은 마감에 참여로 바뀌어 성사(JOINED_GROUP)나 인원 미달(WAITING_FOR_GROUP)이 될 때까지 막는다.
    fun canTakeOneToOne(week: OperationWeek, now: LocalDateTime): Boolean = when (this) {
        JOINED_GROUP, NOT_RESPONDED -> false
        NOT_INVITED, DECLINED -> true
        NO_CANDIDATE_GROUPS, WAITING_FOR_GROUP -> GroupResponseDeadline.hasPassed(week, now)
    }
}
