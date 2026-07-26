ALTER TABLE quiz_set
    ADD COLUMN week_started_on DATE NULL COMMENT '운영 주 시작일 (해당 주 월요일)' AFTER week_no;

-- 기존 행 백필: start_date가 속한 주의 월요일 (WEEKDAY: 월=0 ~ 일=6)
UPDATE quiz_set
SET week_started_on = DATE_SUB(DATE(start_date), INTERVAL WEEKDAY(start_date) DAY);

-- NOT NULL 승격은 하지 않는다 — 구버전 코드의 INSERT는 이 컬럼을 채우지 않으므로,
-- 여기서 승격하면 신버전 배포 전에 적용했을 때 퀴즈셋 쓰기가 실패한다.
-- 승격과 구 컬럼 제거는 신버전 배포 후 다음 마이그레이션(V20260726204352)에서 수행한다.
