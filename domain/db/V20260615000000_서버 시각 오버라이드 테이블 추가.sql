-- 어드민 서버 시각 오버라이드 테이블 — 단일 행 운영.
-- enabled=1 이고 override_date_time 이 있으면 서버가 그 시각을 "현재 시각"으로 사용한다.
-- 비활성화하면(enabled=0) 실제 시각을 사용한다. 설정 변경자(author_name/author_email)를 함께 기록한다.
CREATE TABLE server_time_override
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    enabled            BIT(1)       NOT NULL COMMENT '오버라이드 활성화 여부',
    override_date_time DATETIME(6)  NULL COMMENT '오버라이드된 서버 시각',
    author_name        VARCHAR(50)  NULL COMMENT '최종 설정자 이름',
    author_email       VARCHAR(100) NULL COMMENT '최종 설정자 이메일',
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);
