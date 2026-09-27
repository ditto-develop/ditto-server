-- 대화방별 알림 끄기. 채팅 알림(새 메시지·방 오픈·첫 메시지 리마인드·종료 임박·투표 생성/마감)의 푸시만 막고,
-- 알림 센터 적재는 그대로 둔다 — 전체 채팅 토글(member_notification_setting.chat)과 같은 규칙이다.
ALTER TABLE chat_room_member
    ADD COLUMN muted_at DATETIME(6) NULL COMMENT '이 방 알림을 끈 시각 (켜져 있으면 NULL)' AFTER hidden_at;
