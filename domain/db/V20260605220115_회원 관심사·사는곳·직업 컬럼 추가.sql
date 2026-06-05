ALTER TABLE member
    ADD COLUMN interests VARCHAR(500) NOT NULL DEFAULT '' COMMENT '관심사 (콤마 구분 enum 문자열)' AFTER joined_at,
    ADD COLUMN location  VARCHAR(20)  NULL COMMENT '사는곳' AFTER interests,
    ADD COLUMN job       VARCHAR(30)  NULL COMMENT '직업' AFTER location;
