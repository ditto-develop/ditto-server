-- 주간 식별자가 week_started_on 으로 이관 완료됨에 따라 파생 가능한 중복 컬럼을 제거한다.
-- 적용 순서 주의: week_started_on 을 사용하는 애플리케이션 배포와 함께 적용할 것 (구버전 코드는 year_no 등을 조회함).
ALTER TABLE quiz_set
    DROP INDEX quiz_set_index_1,
    DROP COLUMN year_no,
    DROP COLUMN month_no,
    DROP COLUMN week_no,
    ADD INDEX quiz_set_index_1 (week_started_on);
