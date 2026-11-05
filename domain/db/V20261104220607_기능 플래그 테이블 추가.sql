-- 서버 기능 플래그. 어드민 화면(/admin/feature-flags)에서 켜고 끈다. 플래그당 한 행이고 행이 없으면 꺼진 것이다.
-- 서버는 인스턴스마다 1분 캐시해 읽는다. 배경: docs/adr/0051.
CREATE TABLE feature_flag
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    flag_key             VARCHAR(50) NOT NULL COMMENT '플래그 키 (FeatureFlagKey)',
    enabled              BIT(1)      NOT NULL COMMENT '켜짐 여부',
    enabled_at           DATETIME(6) NULL COMMENT '마지막으로 켠 시각 (꺼져 있으면 NULL)',
    updated_by_member_id BIGINT      NULL COMMENT '마지막으로 바꾼 어드민 회원 ID',
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT feature_flag_uk_1 UNIQUE (flag_key)
);
