package com.ditto.api.admin.quiz

import java.time.LocalDateTime

/** 후보가 없는 이유를 가르는 두 시각. [generatedAt]은 정규 매칭 후보가 처음 생긴 시각이고, 아직 없으면 null 이다. */
class MatchingTimeline(
    val responseDeadline: LocalDateTime,
    val generatedAt: LocalDateTime?,
)
