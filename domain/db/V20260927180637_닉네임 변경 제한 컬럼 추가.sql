-- 닉네임은 2회 바꾸면 14일 동안 잠기고, 잠금이 풀리면 다시 2회를 쓸 수 있다.
-- 이력 테이블 대신 현재 창의 횟수와 잠금 해제 시각만 둔다 — 규칙이 "창 안의 횟수"만 보므로 과거 변경 내역이 필요 없다.
-- 가입 때 정한 닉네임은 세지 않는다.
ALTER TABLE member
    ADD COLUMN nickname_change_count INT NOT NULL DEFAULT 0 COMMENT '현재 창에서 닉네임을 바꾼 횟수' AFTER nickname,
    ADD COLUMN nickname_change_locked_until DATETIME(6) NULL COMMENT '닉네임 변경 잠금 해제 시각 (잠기지 않았으면 NULL)' AFTER nickname_change_count;
