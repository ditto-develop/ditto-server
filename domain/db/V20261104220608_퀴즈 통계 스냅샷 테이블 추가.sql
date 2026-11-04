-- 마감된 퀴즈셋의 통계 스냅샷 (IMP-1 BE-1). 목 00:00 배치가 만들고, 없으면 조회 시 그 자리에서 만든다.
-- 모집단은 마감 시점 완주자(어드민 제외). 마감 뒤 답이 바뀌지 않으므로 한 번 만들면 다시 계산하지 않는다.
CREATE TABLE quiz_set_stat
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    quiz_set_id       BIGINT      NOT NULL COMMENT '퀴즈 세트 ID',
    participant_count INT         NOT NULL COMMENT '마감 시점 완주자 수 (어드민 제외)',
    overlap_threshold INT         NOT NULL COMMENT '''나와 답이 겹친 사람'' 기준 문항 수 = ceil(문항 수 × 2/3), 스냅샷 시점 문항 수로 고정',
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT quiz_set_stat_uk_1 UNIQUE (quiz_set_id)
);

CREATE TABLE quiz_choice_stat
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    quiz_set_id  BIGINT      NOT NULL COMMENT '퀴즈 세트 ID',
    quiz_id      BIGINT      NOT NULL COMMENT '퀴즈 ID',
    choice_id    BIGINT      NOT NULL COMMENT '선택지 ID',
    select_count INT         NOT NULL COMMENT '이 선택지를 고른 완주자 수',
    percent      INT         NOT NULL COMMENT '정수 % (문항 안 합 100 보정 후)',
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT quiz_choice_stat_uk_1 UNIQUE (quiz_set_id, choice_id)
);

CREATE INDEX quiz_choice_stat_index_1 ON quiz_choice_stat (quiz_id);

CREATE TABLE quiz_member_stat
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    quiz_set_id    BIGINT      NOT NULL COMMENT '퀴즈 세트 ID',
    member_id      BIGINT      NOT NULL COMMENT '회원 ID',
    minority_count INT         NOT NULL COMMENT '내 답이 소수 의견이었던 문항 수',
    overlap_count  INT         NOT NULL COMMENT '같은 셋 완주자 중 나와 같은 답이 overlap_threshold 개 이상인 사람 수 (본인 제외)',
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT quiz_member_stat_uk_1 UNIQUE (quiz_set_id, member_id)
);

CREATE INDEX quiz_member_stat_index_1 ON quiz_member_stat (member_id);
