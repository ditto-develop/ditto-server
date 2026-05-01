package com.ditto.api.match.dto

data class PersonalMatchRequest(
    val receiverId: Long,
    val quizSetId: Long,
)
