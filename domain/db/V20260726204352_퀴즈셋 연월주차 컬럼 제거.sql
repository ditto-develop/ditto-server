-- 주간 식별자가 week_started_on 으로 이관 완료됨에 따라 파생 가능한 중복 컬럼을 제거한다.
-- 적용 순서 주의: week_started_on 을 사용하는 신버전 배포가 끝난 뒤에만 적용할 것
-- (구버전 코드는 year_no 등을 조회·기록하고 week_started_on 을 채우지 않는다).

-- 신버전 배포 전 구버전이 남겼을 수 있는 미기록 행을 마저 백필한 뒤 NOT NULL 로 승격한다.
UPDATE quiz_set
SET week_started_on = DATE_SUB(DATE(start_date), INTERVAL WEEKDAY(start_date) DAY)
WHERE week_started_on IS NULL;

ALTER TABLE quiz_set
    MODIFY COLUMN week_started_on DATE NOT NULL COMMENT '운영 주 시작일 (해당 주 월요일)';

-- 같은 인덱스명 DROP+ADD 를 한 문장에 섞으면 MySQL 버전에 따라 거부될 수 있어 문을 분리한다.
ALTER TABLE quiz_set
    DROP INDEX quiz_set_index_1,
    DROP COLUMN year_no,
    DROP COLUMN month_no,
    DROP COLUMN week_no;

ALTER TABLE quiz_set
    ADD INDEX quiz_set_index_1 (week_started_on);
