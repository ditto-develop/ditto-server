ALTER TABLE member
    ADD COLUMN name         VARCHAR(50) NULL COMMENT '이름' AFTER id,
    ADD COLUMN phone_number VARCHAR(20) NULL COMMENT '전화번호' AFTER email,
    ADD COLUMN gender       VARCHAR(10) NULL COMMENT '성별 (MALE, FEMALE)' AFTER phone_number,
    ADD COLUMN age          INT         NULL COMMENT '나이대' AFTER gender,
    ADD COLUMN birth_date   DATETIME(6) NULL COMMENT '생년월일' AFTER age,
    ADD COLUMN joined_at    DATETIME(6) NULL COMMENT '가입일시' AFTER birth_date;

ALTER TABLE member
    ADD UNIQUE INDEX member_unique_1 (nickname);
