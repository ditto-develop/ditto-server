-- 탈퇴 때 애플 토큰을 폐기하려고 로그인 때 인가 코드를 교환해 받은 refresh token 을 둔다.
-- 폐기는 토큰을 받은 client_id(앱은 번들 ID, 웹은 Services ID)로 해야 해서 함께 둔다. 지금은 애플만 채운다.
ALTER TABLE social_account
    ADD COLUMN provider_refresh_token VARCHAR(512) NULL COMMENT '제공자 refresh token (탈퇴 시 폐기용, 없으면 NULL)',
    ADD COLUMN provider_client_id VARCHAR(100) NULL COMMENT 'refresh token 을 받은 client_id';
