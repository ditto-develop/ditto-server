package com.ditto.api.system.dto

/** 요청한 기능 플래그의 켜짐 여부. 키는 요청한 이름 그대로이고, 모르는 이름이나 DB 에 행이 없는 플래그는 false. */
data class FeatureFlagsResponse(
    val flags: Map<String, Boolean>,
)
