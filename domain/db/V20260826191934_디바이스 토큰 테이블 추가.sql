-- 푸시 주소록. 앱이 FCM 에서 받은 디바이스 토큰의 소유 회원. 회원 하나가 기기 여러 개 가능.
-- token 단독 유일키 = "한 토큰 = 한 회원". 토큰은 기기 소속이라 로그아웃해도 남으므로,
-- 같은 기기에서 다른 회원이 로그인하면 행 추가가 아니라 소유자 갱신이다.
CREATE TABLE member_device
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL COMMENT '소유 회원 ID',
    -- utf8mb4_bin: 기본 조합(_ai_ci)은 대소문자를 무시해 대소문자만 다른 두 토큰이 유일키에 부딪힌다.
    token      VARCHAR(512) COLLATE utf8mb4_bin NOT NULL COMMENT 'FCM 등록 토큰',
    platform   VARCHAR(10)  NOT NULL COMMENT '기기 플랫폼 (IOS, ANDROID)',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT member_device_uk_1 UNIQUE (token)
);

-- 발송·탈퇴 정리가 회원의 기기 목록을 읽는 경로.
CREATE INDEX member_device_index_1 ON member_device (member_id);
