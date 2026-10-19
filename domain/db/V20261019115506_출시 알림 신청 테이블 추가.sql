-- launch_notification_request 테이블
CREATE TABLE launch_notification_request
(
    id              BIGINT       AUTO_INCREMENT NOT NULL COMMENT '출시 알림 신청 ID',
    email           VARCHAR(254) NOT NULL COMMENT '이메일 (trim·소문자 정규화)',
    platform        VARCHAR(20)  NOT NULL COMMENT '출시 알림 받을 플랫폼 (enum 이름)',
    consent_version VARCHAR(30)  NOT NULL COMMENT '동의한 개인정보 수집 문구 버전 (FE가 보여 준 문구)',
    consented_at    DATETIME(6)  NOT NULL COMMENT '마지막으로 동의한 일시',
    notified_at     DATETIME(6)  NULL COMMENT '출시 안내를 보낸 일시 (보유 기간 계산 기준)',
    created_at      DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at      DATETIME(6)  NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_launch_notification_request PRIMARY KEY (id),
    CONSTRAINT launch_notification_request_uk_1 UNIQUE (email, platform)
);

CREATE INDEX launch_notification_request_index_1 ON launch_notification_request (notified_at);
