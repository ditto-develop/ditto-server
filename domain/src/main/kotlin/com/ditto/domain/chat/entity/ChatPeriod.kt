package com.ditto.domain.chat.entity

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * 채팅이 열려 있는 주말 구간. 금요일 00:00에 열고 다음 월요일 00:00에 닫는 72시간이다.
 *
 * 일반 매칭 채팅과 재매칭 채팅이 같은 창을 쓰므로 계산을 여기 한 곳에 둔다 —
 * 두 곳에서 각자 계산하면 "그 주말이 언제인가"의 정의가 갈라진다.
 */
data class ChatPeriod(
    val opensAt: LocalDateTime,
    val expiresAt: LocalDateTime,
) {
    init {
        require(opensAt < expiresAt) { "채팅 개방 시각은 종료 시각보다 앞서야 합니다: $opensAt ~ $expiresAt" }
    }

    fun isOpenedAt(at: LocalDateTime): Boolean = at >= opensAt

    companion object {
        /**
         * [at]이 속한 운영 주(월~일)의 주말 구간. 주중에 성사되든 주말 도중에 성사되든 같은 주말을 가리킨다.
         *
         * 주말이 이미 시작된 뒤에 만들어지는 방(운영 복구·주말 중 성사)도 다음 주로 미루지 않고
         * 진행 중인 주말에 합류시킨다 — 개방 시각이 과거면 [isOpenedAt]이 곧바로 참이 된다.
         */
        fun weekendOf(at: LocalDateTime): ChatPeriod {
            val friday = at.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusDays(FRIDAY_OFFSET_FROM_MONDAY)
            return of(friday)
        }

        /** 특정 금요일로 구간을 만든다. */
        fun of(friday: LocalDate): ChatPeriod {
            require(friday.dayOfWeek == DayOfWeek.FRIDAY) { "채팅 개방일은 금요일이어야 합니다: $friday" }
            return ChatPeriod(
                opensAt = friday.atStartOfDay(),
                expiresAt = friday.plusDays(DURATION_DAYS).atStartOfDay(),
            )
        }

        private const val FRIDAY_OFFSET_FROM_MONDAY = 4L

        /** 금 00:00 ~ 월 00:00 = 72시간 */
        private const val DURATION_DAYS = 3L
    }
}
