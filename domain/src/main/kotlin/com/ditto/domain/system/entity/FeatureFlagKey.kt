package com.ditto.domain.system.entity

/**
 * 어드민이 켜고 끄는 서버 기능. 행이 없으면 꺼진 것으로 본다 — 새 값을 추가해도 마이그레이션 없이 꺼진 채로 시작한다.
 *
 * 이름(`name`)이 DB 키라서 **바꾸면 그 플래그는 꺼진다.** 값을 없앨 때는 행도 함께 지운다.
 */
enum class FeatureFlagKey(val description: String) {
    QUIZ_CHAT("대화방 첫 대화 힌트 메시지와 질문 카드 전송 (IMP-1)"),

    /** 서버 동작은 없다. FE 가 `GET /api/v1/feature-flags` 로 읽어 화면을 보이거나 감춘다. */
    RECORDS("기록 탭·대화방 '우리의 답변' 화면 (FE)"),
}
