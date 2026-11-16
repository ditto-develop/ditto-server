package com.ditto.api.admin.dummy.dto

/** 더미 생성 폼(스프링 폼 바인딩). */
class DummyGenerateForm(
    var quizSetId: Long = 0L,
    var maleCount: Int = 0,
    var femaleCount: Int = 0,
    var minAge: Int = 20,
    var maxAge: Int = 35,
    /** 완주 시각을 지금 서버 시각으로 남긴다. 응답 마감 뒤면 시크릿 매칭 늦은 완주자다. */
    var completedAtServerTime: Boolean = false,
)
