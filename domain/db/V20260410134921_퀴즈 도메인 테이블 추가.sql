CREATE TABLE quiz_set
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '퀴즈 세트 ID',
    year_no       INT          NOT NULL COMMENT '년도',
    month_no      INT          NOT NULL COMMENT '월',
    week_no       INT          NOT NULL COMMENT '주차',
    category      VARCHAR(50)  NOT NULL COMMENT '카테고리',
    title         VARCHAR(100) NOT NULL COMMENT '퀴즈 세트 제목',
    description   VARCHAR(500) NULL COMMENT '퀴즈 세트 설명',
    start_date    DATETIME(6)  NOT NULL COMMENT '시작일시',
    end_date      DATETIME(6)  NOT NULL COMMENT '종료일시',
    is_active     TINYINT(1)   NOT NULL COMMENT '활성화 여부',
    matching_type VARCHAR(20)  NOT NULL COMMENT '매칭 타입 (ONE_TO_ONE, GROUP)',
    created_at    DATETIME(6)  NOT NULL COMMENT '생성 일시',
    updated_at    DATETIME(6)  NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    INDEX quiz_set_index_1 (year_no, month_no, week_no),
    INDEX quiz_set_index_2 (start_date, end_date, is_active)
) COMMENT ='퀴즈 세트';

CREATE TABLE quiz
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '퀴즈 ID',
    quiz_set_id   BIGINT       NOT NULL COMMENT '퀴즈 세트 ID',
    question      VARCHAR(500) NOT NULL COMMENT '퀴즈 질문',
    display_order INT          NOT NULL COMMENT '퀴즈 노출 순서',
    created_at    DATETIME(6)  NOT NULL COMMENT '생성 일시',
    updated_at    DATETIME(6)  NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    INDEX quiz_index_1 (quiz_set_id, display_order)
) COMMENT ='퀴즈';

CREATE TABLE quiz_choice
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '퀴즈 선택지 ID',
    quiz_id       BIGINT       NOT NULL COMMENT '퀴즈 ID',
    content       VARCHAR(200) NOT NULL COMMENT '선택지 내용',
    display_order INT          NOT NULL COMMENT '선택지 노출 순서(1부터 시작)',
    created_at    DATETIME(6)  NOT NULL COMMENT '생성 일시',
    updated_at    DATETIME(6)  NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    INDEX quiz_choice_index_1 (quiz_id, display_order)
) COMMENT ='퀴즈 선택지';
