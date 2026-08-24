-- 그룹 채팅 개별 이탈(#142)의 저장 기반.
--
-- 이탈은 행 삭제가 아니라 left_at 소프트 컬럼으로 남긴다. 행을 지우면
-- ① 읽음 커서(last_read_message_id)가 사라져 재조회 시 안읽음 수가 전체 메시지 수로 튀고,
-- ② 마이프로필 matchCount(chat_room_member 행 수)가 과거로 소급 감소하며,
-- ③ 과거 SYSTEM 메시지의 sender_id 를 해석할 근거가 없어진다.
ALTER TABLE chat_room_member
    ADD COLUMN left_at DATETIME(6) NULL COMMENT '방 이탈 시각 (참여 중이면 NULL)' AFTER last_read_message_id;

-- 종료 사유에 INSUFFICIENT_MEMBERS(인원 미달 자동 해체)가 추가된다.
-- 값이 정확히 20자라 기존 VARCHAR(20)에는 여유가 0 이므로 폭을 넓힌다.
ALTER TABLE chat_room
    MODIFY COLUMN end_reason VARCHAR(30) NULL COMMENT '종료 사유 (EXPIRED, USER_ENDED, INSUFFICIENT_MEMBERS)';
