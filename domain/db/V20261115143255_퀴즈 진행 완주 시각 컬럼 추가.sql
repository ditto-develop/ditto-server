-- 시크릿 매칭(IMP-2): 늦게 완주한 회원을 정규 매칭·통계와 가르려고 완주 시각을 남긴다.
-- 기존 행은 채우지 않는다. null 은 마감 전에 완주한 것으로 읽는다.
ALTER TABLE quiz_progress
    ADD COLUMN completed_at DATETIME(6) NULL COMMENT '완주 시각 (서버 시각)' AFTER status;
