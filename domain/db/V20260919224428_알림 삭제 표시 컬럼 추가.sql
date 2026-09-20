-- 사용자가 알림 센터에서 지운 알림을 표시하는 컬럼.
-- 행을 지우지 않는 이유는 중복 검사(notification_index_3) 때문이다. 검사가 행의 존재를 보므로
-- 지워버리면 스케줄러가 같은 대상을 다시 집어와 알림과 푸시를 또 내보낸다.
-- 지운 알림은 목록·미읽음 수에서 빠지고, 30일이 지나면 기존 purge 배치가 다른 행과 함께 지운다.
ALTER TABLE notification
    ADD COLUMN deleted_at DATETIME(6) NULL COMMENT '사용자가 지운 시각 (안 지웠으면 NULL)';

-- 목록 조회가 member_id + deleted_at IS NULL 로 자른 뒤 id DESC 로 커서 페이징한다.
DROP INDEX notification_index_1 ON notification;
CREATE INDEX notification_index_1 ON notification (member_id, deleted_at, id);
