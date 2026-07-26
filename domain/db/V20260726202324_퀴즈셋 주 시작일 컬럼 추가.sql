ALTER TABLE quiz_set
    ADD COLUMN week_started_on DATE NULL COMMENT '운영 주 시작일 (해당 주 월요일)' AFTER week_no;

-- 기존 행 백필: start_date가 속한 주의 월요일 (WEEKDAY: 월=0 ~ 일=6)
UPDATE quiz_set
SET week_started_on = DATE_SUB(DATE(start_date), INTERVAL WEEKDAY(start_date) DAY);

ALTER TABLE quiz_set
    MODIFY COLUMN week_started_on DATE NOT NULL COMMENT '운영 주 시작일 (해당 주 월요일)';
