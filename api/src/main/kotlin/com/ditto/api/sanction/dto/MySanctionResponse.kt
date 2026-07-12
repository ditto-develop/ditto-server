package com.ditto.api.sanction.dto

/**
 * 내 활성 제재 응답. 제재가 없으면 [sanction]이 null.
 */
data class MySanctionResponse(
    val sanction: EffectiveSanctionResponse?,
)
