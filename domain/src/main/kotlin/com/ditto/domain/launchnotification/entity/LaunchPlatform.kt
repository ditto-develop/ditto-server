package com.ditto.domain.launchnotification.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 출시 알림을 신청할 수 있는 플랫폼. 지금은 아직 출시 전인 안드로이드뿐이다.
 * 값 추가 전용 — 배포된 이름은 바꾸거나 지우지 않는다(저장값이 enum 이름이다).
 */
enum class LaunchPlatform(
    val description: String,
) {
    ANDROID("안드로이드"),
    ;

    companion object {
        fun from(name: String): LaunchPlatform =
            entries.firstOrNull { it.name == name }
                ?: throw WarnException(ErrorCode.BAD_REQUEST, "지원하지 않는 플랫폼입니다.")
    }
}
