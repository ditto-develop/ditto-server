package com.ditto.api.admin.dummy.dto

import com.ditto.domain.member.entity.GenderPreference

/** 더미 참여자 생성 폼(스프링 폼 바인딩). */
class DummyGenerateForm(
    var quizSetId: Long = 0L,
    var maleCount: Int = 0,
    var femaleCount: Int = 0,
    var minAge: Int = 20,
    var maxAge: Int = 35,
    var preferredGender: GenderPreference = GenderPreference.OPPOSITE,
)
