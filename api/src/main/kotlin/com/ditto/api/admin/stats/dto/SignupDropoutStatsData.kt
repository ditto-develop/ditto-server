package com.ditto.api.admin.stats.dto

import com.ditto.domain.member.repository.querydsl.QuizBlockedMember
import com.ditto.domain.member.repository.querydsl.SignupDropout
import com.ditto.domain.member.repository.querydsl.SocialProviderCount

data class SignupDropoutStatsData(
    val dropoutHours: Long,
    val signupDropouts: List<SignupDropout>,
    val joinedCountsBySocialProvider: List<SocialProviderCount>,
    val minIntroNoteCountForQuiz: Long,
    val quizBlockedMembers: List<QuizBlockedMember>,
    val activeMemberCount: Long,
)
