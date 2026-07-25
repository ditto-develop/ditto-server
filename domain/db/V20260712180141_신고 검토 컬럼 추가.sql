-- member_report: 어드민 검토 결과 컬럼 추가
ALTER TABLE member_report
    ADD COLUMN reviewed_by BIGINT NULL COMMENT '검토자 회원 ID' AFTER status,
    ADD COLUMN reviewer_name VARCHAR(50) NULL COMMENT '검토자 표시명 스냅샷 (계정 삭제 후에도 감사 기록 보존)' AFTER reviewed_by,
    ADD COLUMN reviewed_at DATETIME(6) NULL COMMENT '검토 일시' AFTER reviewer_name,
    ADD COLUMN review_note VARCHAR(500) NULL COMMENT '검토 메모' AFTER reviewed_at;
