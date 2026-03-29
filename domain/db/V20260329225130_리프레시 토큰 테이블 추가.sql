CREATE TABLE refresh_token
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '리프레시 토큰 ID',
    member_id  BIGINT      NOT NULL COMMENT '회원 ID',
    token      VARCHAR(36) NOT NULL COMMENT '리프레시 토큰 (UUID)',
    expires_at DATETIME(6) NOT NULL COMMENT '만료 일시',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_token (token),
    INDEX      refresh_token_index_1 (member_id)
) COMMENT='리프레시 토큰';

