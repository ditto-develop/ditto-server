-- 퀴즈 통계 모집단에 어드민도 넣는다(#367). 컬럼 주석만 바꾼다.
ALTER TABLE quiz_set_stat
    MODIFY COLUMN participant_count INT NOT NULL COMMENT '마감 시점 완주자 수';
