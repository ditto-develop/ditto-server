-- push_send_history 테이블
CREATE TABLE push_send_history
(
    id                  BIGINT       AUTO_INCREMENT NOT NULL COMMENT '푸시 발송 이력 ID',
    notification_id     BIGINT       NOT NULL COMMENT '알림 ID',
    member_id           BIGINT       NOT NULL COMMENT '수신 회원 ID',
    notification_type   VARCHAR(30)  NOT NULL COMMENT '알림 유형',
    skip_reason         VARCHAR(20)  NULL COMMENT '보내지 않고 건너뛴 이유 (보냈으면 NULL)',
    device_count        INT          NOT NULL COMMENT '보낸 기기 수 (건너뛰었으면 0)',
    failed_device_count INT          NOT NULL COMMENT '실패한 기기 수',
    failure_codes       VARCHAR(255) NULL COMMENT 'FCM 실패 코드 (겹치지 않게 쉼표로 이음, 실패가 없으면 NULL)',
    created_at          DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at          DATETIME(6)  NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_push_send_history PRIMARY KEY (id)
);

CREATE INDEX push_send_history_index_1 ON push_send_history (created_at);

CREATE INDEX push_send_history_index_2 ON push_send_history (member_id, created_at);
