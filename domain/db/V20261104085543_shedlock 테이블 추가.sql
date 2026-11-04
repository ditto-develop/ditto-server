-- shedlock 테이블 (스케줄러 중복 실행 방지 락)
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL COMMENT '락 이름 (스케줄 작업 이름)',
    lock_until DATETIME(6)  NOT NULL COMMENT '락 만료 시각 (DB 시각, UTC)',
    locked_at  DATETIME(6)  NOT NULL COMMENT '락 획득 시각 (DB 시각, UTC)',
    locked_by  VARCHAR(255) NOT NULL COMMENT '락을 잡은 인스턴스 (호스트명)',
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
