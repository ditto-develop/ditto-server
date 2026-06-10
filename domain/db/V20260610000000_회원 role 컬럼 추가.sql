-- 회원 권한(role) 컬럼 추가 — 어드민 인가용
-- ADMIN 부여는 운영자가 수동으로 실행한다 (대상 회원은 가입 완료(ACTIVE) 상태여야 함):
--   UPDATE member SET role = 'ADMIN' WHERE id = ?;
ALTER TABLE member
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '회원 권한 (USER, ADMIN)' AFTER status;
