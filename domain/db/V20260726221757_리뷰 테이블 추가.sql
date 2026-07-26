-- 평가(리뷰) 기반 테이블 2종.
-- member_review: 한 회원이 한 채팅 종료 건에 대해 진행하는 평가.
--                match_type + match_id 는 chat_room 의 (source_type, source_id) 와 같은 값을 조인 없이 읽으려고 복사해 둔다.
--                양쪽 다 생성 후 불변이라 어긋나지 않는다.
-- review_answer: 그 안의 평가 대상별 응답. 진행 단위 생성 시 빈 행으로 만들어지고 이후 답변이 채워진다.
--                화면 노출 순서는 생성 순서와 같아 별도 순서 컬럼 없이 id 정렬로 처리한다.
CREATE TABLE member_review
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    author_member_id BIGINT      NOT NULL COMMENT '리뷰 작성자 회원 ID',
    match_type       VARCHAR(20) NOT NULL COMMENT '매칭 유형 (PERSONAL, GROUP)',
    match_id         BIGINT      NOT NULL COMMENT '매칭 ID (personal_match 또는 group_match 의 ID)',
    chat_room_id     BIGINT      NOT NULL COMMENT '평가 문맥이 된 채팅방 ID',
    quiz_set_id      BIGINT      NOT NULL COMMENT '원본 퀴즈셋 ID',
    week_started_on  DATE        NOT NULL COMMENT '운영 주 시작일 (해당 주 월요일)',
    status           VARCHAR(20) NOT NULL COMMENT '진행 상태 (NOT_STARTED, IN_PROGRESS, COMPLETED)',
    available_at     DATETIME(6) NOT NULL COMMENT '평가 가능 시각 (채팅 종료 시각)',
    completed_at     DATETIME(6) NULL COMMENT '전체 완료 시각 (미완료면 NULL)',
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 동일 종료 이벤트를 다시 처리해도 회원별 진행 단위는 하나만 존재한다.
    CONSTRAINT member_review_uk_1 UNIQUE (chat_room_id, author_member_id)
);

CREATE INDEX member_review_index_1 ON member_review (author_member_id, status, available_at);

CREATE TABLE review_answer
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    member_review_id   BIGINT      NOT NULL COMMENT '리뷰 진행 단위 ID',
    reviewed_member_id BIGINT      NOT NULL COMMENT '평가 대상 회원 ID',
    meeting_status     VARCHAR(30) NULL COMMENT '오프라인 만남 성사 여부 (MET, APPOINTMENT_MADE, CHAT_ONLY, NO_SHOW). 미응답이면 NULL',
    rating             INT         NULL COMMENT '별점 1~5 (미응답이면 NULL)',
    comment            VARCHAR(50) NULL COMMENT '한줄 코멘트 (선택)',
    -- 대상별 제출은 최종 확정이라 상태가 미응답/응답완료 둘뿐이다. 별도 상태 컬럼 없이 이 값으로 판정한다.
    answered_at        DATETIME(6) NULL COMMENT '최종 제출 시각. NULL 이면 미응답',
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT review_answer_uk_1 UNIQUE (member_review_id, reviewed_member_id)
);

CREATE INDEX review_answer_index_1 ON review_answer (reviewed_member_id, answered_at);
