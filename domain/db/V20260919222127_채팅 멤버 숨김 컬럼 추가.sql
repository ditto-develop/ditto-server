-- 종료된 방을 내 목록에서만 감추는 기능(#201).
-- left_at 은 재사용할 수 없다. 이탈은 퇴장 SYSTEM 메시지가 나가고 투표·안읽음 집계에서도 빠지는 별개 상태다.
ALTER TABLE chat_room_member
    ADD COLUMN hidden_at DATETIME(6) NULL COMMENT '내 목록에서 숨긴 시각 (보이면 NULL)' AFTER left_at;
