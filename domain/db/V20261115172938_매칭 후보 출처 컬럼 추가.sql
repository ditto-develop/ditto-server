-- 시크릿 매칭(IMP-2): 시크릿 매칭이 같은 퀴즈셋에 후보를 더하므로 어느 매칭이 만든 후보인지 남긴다.
-- 기존 행은 모두 정규 매칭 후보라 기본값 REGULAR 가 맞다.
ALTER TABLE match_candidate
    ADD COLUMN candidate_origin VARCHAR(20) NOT NULL DEFAULT 'REGULAR' COMMENT '후보를 만든 매칭 (REGULAR, SECRET_FIRST, SECRET_SECOND)' AFTER quiz_set_id;
