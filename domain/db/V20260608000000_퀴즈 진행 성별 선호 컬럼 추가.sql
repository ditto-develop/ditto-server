ALTER TABLE quiz_progress
    ADD COLUMN preferred_gender VARCHAR(20) NOT NULL DEFAULT 'OPPOSITE' COMMENT '매칭 성별 선호 (OPPOSITE, SAME, ANY)' AFTER total_count;
