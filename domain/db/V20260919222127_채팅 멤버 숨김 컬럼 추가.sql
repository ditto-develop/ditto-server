-- 종료된 방을 내 목록에서만 감추는 기능(#201)의 저장 기반.
--
-- left_at 을 재사용하지 않는다. 이탈은 상대에게 퇴장 SYSTEM 메시지가 나가고
-- 투표 집계·메시지별 안읽음 수 대상에서도 빠지는 별개 상태라, 같은 컬럼에 담으면
-- "감췄을 뿐인 사람"이 나간 사람으로 집계된다.
ALTER TABLE chat_room_member
    ADD COLUMN hidden_at DATETIME(6) NULL COMMENT '내 목록에서 숨긴 시각 (보이면 NULL)' AFTER left_at;
