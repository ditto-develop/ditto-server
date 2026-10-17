-- feedback 테이블
CREATE TABLE feedback
(
    id           BIGINT        AUTO_INCREMENT NOT NULL COMMENT '의견 ID',
    member_id    BIGINT        NOT NULL COMMENT '보낸 회원 ID',
    type         VARCHAR(20)   NOT NULL COMMENT '의견 유형 (enum 이름)',
    content      VARCHAR(1000) NOT NULL COMMENT '내용',
    app_version  VARCHAR(30)   NULL COMMENT '앱 버전',
    os           VARCHAR(30)   NULL COMMENT 'OS 이름과 버전',
    device_model VARCHAR(50)   NULL COMMENT '기기 모델',
    status       VARCHAR(20)   NOT NULL COMMENT '처리 상태 (enum 이름)',
    created_at   DATETIME(6)   NOT NULL COMMENT '생성일시',
    updated_at   DATETIME(6)   NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_feedback PRIMARY KEY (id)
);

CREATE INDEX feedback_index_1 ON feedback (status, created_at);

-- feedback_image 테이블
CREATE TABLE feedback_image
(
    id            BIGINT       AUTO_INCREMENT NOT NULL COMMENT '의견 첨부 이미지 ID',
    feedback_id   BIGINT       NOT NULL COMMENT '의견 ID',
    object_key    VARCHAR(200) NOT NULL COMMENT 'S3 객체 키',
    display_order INT          NOT NULL COMMENT '첨부 순서 (0부터)',
    created_at    DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at    DATETIME(6)  NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_feedback_image PRIMARY KEY (id),
    CONSTRAINT feedback_image_uk_1 UNIQUE (feedback_id, display_order)
);
