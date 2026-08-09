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
         * [at] 이후 처음 열리는 주말 — "다가오는 금요일 00:00". 개방 시각이 [at]보다 앞서지 않으므로
         * **이미 닫힌 구간이 나올 수 없다.**
         *
         * 재매칭 채팅이 쓴다. 기획이 "금요일 00:00 채팅방 오픈"으로만 정해 특정 주말을 약속하지 않으므로,
         * 성사가 주말 도중이면 남은 시간에 급히 열지 않고 온전한 72시간을 받는 다음 금요일로 간다.
         * 진행 중인 주말에 합류시키는 [weekendOf]와 그 점이 다르다.
         */
        fun upcomingWeekendFrom(at: LocalDateTime): ChatPeriod {
            val weekend = of(at.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)))
            // 금요일 00:00 을 이미 지났다면 그 주말은 시작된 것이므로 다음 금요일로 넘긴다.
            return weekend.takeIf { it.opensAt >= at } ?: of(weekend.opensAt.toLocalDate().plusWeeks(1))
        }

        /**
         * [at]이 속한 운영 주(월~일)의 주말 구간. 일반 매칭 채팅이 쓴다 — 수락 즉시 대화할 수 있어야 하므로
         * 주말이 이미 시작됐으면 다음 주로 미루지 않고 진행 중인 주말에 합류시킨다(개방 시각이 과거면
         * [isOpenedAt]이 곧바로 참이 된다).
         *
         * **[at]이 주말 도중이면 개방 시각이 과거인 구간을 돌려준다.** 그래서 과거 시각으로 부르면
         * 이미 닫힌 구간이 나올 수 있다 — 방을 만드는 쪽은 만드는 시점으로 부르거나
         * [upcomingWeekendFrom]을 써야 한다.
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
