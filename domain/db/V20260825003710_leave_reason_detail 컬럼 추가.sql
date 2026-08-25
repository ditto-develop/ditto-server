-- 탈퇴 사유 자유 입력(#144, FE 요구서 7번 A안).
-- leave_reason 은 탈퇴 화면 선택지 code 전용으로 유지하고, "기타" 선택 시의 자유 서술은
-- 별도 컬럼으로 분리한다 — 한 컬럼에 code 와 서술이 섞이면 사유 집계 때 파싱이 필요해진다.
ALTER TABLE member
    ADD COLUMN leave_reason_detail VARCHAR(100) NULL COMMENT '탈퇴 사유 자유 입력 (선택, 최대 100자)' AFTER leave_reason;
