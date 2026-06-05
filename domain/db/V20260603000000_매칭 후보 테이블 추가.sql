-- match_candidate 테이블
-- 매칭 알고리즘이 계산한 추천 후보 노출 리스트.
-- 페어(A,B)당 (A→B), (B→A) 두 방향(row)으로 저장하여 "내 후보 조회"를 단순화한다.
-- (owner_member_id, other_member_id, quiz_set_id) 유일
CREATE TABLE match_candidate
(
    id              BIGINT AUTO_INCREMENT NOT NULL COMMENT '매칭 후보 ID',
    owner_member_id BIGINT      NOT NULL COMMENT '후보를 노출받는 회원 ID (조회 주체)',
    other_member_id BIGINT      NOT NULL COMMENT '노출되는 상대 회원 ID',
    quiz_set_id     BIGINT      NOT NULL COMMENT '퀴즈 세트 ID',
    score                  DOUBLE      NOT NULL COMMENT '매칭 점수 (0.0 ~ 100.0)',
    matched_question_count INT         NOT NULL COMMENT '일치한 문항 수',
    total_question_count   INT         NOT NULL COMMENT '전체 비교 문항 수',
    created_at      DATETIME(6) NOT NULL COMMENT '생성일시',
    updated_at      DATETIME(6) NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_match_candidate PRIMARY KEY (id),
    CONSTRAINT match_candidate_uk_1 UNIQUE (owner_member_id, other_member_id, quiz_set_id)
);

CREATE INDEX match_candidate_index_1 ON match_candidate (owner_member_id, quiz_set_id, score);
