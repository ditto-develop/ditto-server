package com.ditto.api.match.dto

data class PersonalMatchListResponse(
    val sent: List<PersonalMatchResponse>,
    val received: List<PersonalMatchResponse>,
)
