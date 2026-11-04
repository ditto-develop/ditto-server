package com.ditto.api.config

/**
 * 크론 락(`@SchedulerLock`) 보유 시간 묶음. 규칙과 배경은 ADR 0049.
 *
 * - `AT_MOST`: 락을 쥔 인스턴스가 죽었을 때 풀리는 시간. 실제 실행보다 넉넉하게 잡는다 —
 *   짧으면 실행 도중 풀려 다른 인스턴스가 같은 작업을 또 시작한다.
 * - `AT_LEAST`: 작업이 빨리 끝나도 쥐고 있는 시간. 인스턴스마다 크론이 발화하는 시각 차이(로컬 시계 기준)보다 길고
 *   회차 간격보다 짧게 잡는다. 30초면 QA가 크론을 매분으로 바꿔도 회차를 건너뛰지 않는다.
 */
object SchedulerLockDurations {
    /** 매분 작업(채팅방 생명주기). */
    const val EVERY_MINUTE_AT_MOST = "5m"
    const val EVERY_MINUTE_AT_LEAST = "10s"

    /** 주 1회 작업(퀴즈 오픈·마감 임박·평가 리마인드 푸시, 매칭). */
    const val WEEKLY_AT_MOST = "30m"

    /** 그 외(매시 지연 탈퇴, 매일 보관 기간 정리). [SchedulingConfig]의 기본값이기도 하다. */
    const val DEFAULT_AT_MOST = "10m"

    /** 매분 작업이 아닌 것 전부. */
    const val AT_LEAST = "30s"
}
