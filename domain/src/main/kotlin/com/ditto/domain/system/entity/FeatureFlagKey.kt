package com.ditto.domain.system.entity

/**
 * 어드민이 켜고 끄는 서버 기능. 행이 없으면 꺼진 것으로 본다 — 새 값을 추가해도 마이그레이션 없이 꺼진 채로 시작한다.
 *
 * 이름(`name`)이 DB 키라서 **바꾸면 그 플래그는 꺼진다.** 값을 없앨 때는 행도 함께 지운다 — 남은 행은 읽을 때
 * 건너뛰지만([FeatureFlagRepository.findAllByKeyIn]) 어드민 화면에도 안 보여 정리할 길이 없다.
 * 공개를 마쳐 지운 키와 그 응답은 `FeatureFlags.RETIRED_ALWAYS_ON` 에 남긴다.
 */
enum class FeatureFlagKey(val description: String) {
    SECRET_MATCHING("시크릿 매칭: 마감 뒤 1:1 응시와 그룹 완주자 1:1 (IMP-2)"),
}
