-- 재매칭 취소 사유. 배경은 ADR 0012 — 사유가 하나뿐일 때는 status 에서 도출돼 컬럼을 유예했다.
--
-- 추가만 하므로 롤링 배포와 호환된다. 구버전은 이 컬럼을 모르고 무시한다.
ALTER TABLE rematch
    ADD COLUMN cancel_reason VARCHAR(20) NULL COMMENT '취소 사유 (NOT_MUTUAL, MEMBER_LEFT)';

-- 기존 CANCELLED 행은 정의상 전부 "상호 선택이 아님"이다 — 의미가 바뀌지 않는 백필이다.
UPDATE rematch SET cancel_reason = 'NOT_MUTUAL' WHERE status = 'CANCELLED';
