-- feedback: 어드민 처리 완료 기록 컬럼 추가
ALTER TABLE feedback
    ADD COLUMN completed_by_name VARCHAR(200) NULL COMMENT '처리 완료한 관리자 표시명 스냅샷' AFTER status,
    ADD COLUMN completed_at DATETIME(6) NULL COMMENT '처리 완료 일시' AFTER completed_by_name;
