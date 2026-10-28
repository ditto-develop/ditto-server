package com.ditto.api.admin.stats.dto

import com.ditto.domain.member.repository.querydsl.QuizBlockedMember
import com.ditto.domain.member.repository.querydsl.SignupDropout
import com.ditto.domain.member.repository.querydsl.SocialProviderCount
import java.time.LocalDate

data class SignupDropoutStatsData(
    val dropoutHours: Long,
    val realToday: LocalDate,
    val signupDropouts: List<SignupDropout>,
    val joinedCountsBySocialProvider: List<SocialProviderCount>,
    val minIntroNoteCountForQuiz: Long,
    val quizBlockedMembers: List<QuizBlockedMember>,
    val activeStatusMemberCount: Long,
)
