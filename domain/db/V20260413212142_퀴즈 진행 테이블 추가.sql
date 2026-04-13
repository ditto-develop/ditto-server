CREATE TABLE quiz_progress
(
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '퀴즈 진행 ID',
    member_id      BIGINT      NOT NULL COMMENT '회원 ID',
    quiz_set_id    BIGINT      NOT NULL COMMENT '퀴즈 세트 ID',
    status         VARCHAR(20) NOT NULL COMMENT '진행 상태 (NOT_STARTED, IN_PROGRESS, COMPLETED)',
    answered_count INT         NOT NULL DEFAULT 0 COMMENT '답변한 퀴즈 수',
    total_count    INT         NOT NULL COMMENT '전체 퀴즈 수',
    created_at     DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at     DATETIME(6) NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY quiz_progress_uk_1 (member_id, quiz_set_id),
    INDEX quiz_progress_index_1 (quiz_set_id, status)
) COMMENT ='퀴즈 진행';
