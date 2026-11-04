package com.ditto.api.system.featureflag

import com.ditto.domain.system.entity.FeatureFlagKey

/**
 * 플래그가 **꺼져 있다가 켜지는 순간** 할 일. 켜는 트랜잭션 안에서 불린다 — 실패하면 켜기도 되돌아간다.
 * 켜기 전부터 진행 중이던 대상을 기능에서 빼 두는 데 쓴다(시각 비교로 거르면 서버 시각·실제 시각이 섞인다, ADR 0051).
 */
interface FeatureFlagEnableListener {
    val key: FeatureFlagKey

    fun onEnabled()
}
