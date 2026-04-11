CREATE TABLE quiz_answer
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '퀴즈 답변 ID',
    member_id  BIGINT      NOT NULL COMMENT '회원 ID',
    quiz_id    BIGINT      NOT NULL COMMENT '퀴즈 ID',
    choice_id  BIGINT      NOT NULL COMMENT '선택지 ID',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY quiz_answer_uk_1 (member_id, quiz_id)
) COMMENT ='퀴즈 답변';
