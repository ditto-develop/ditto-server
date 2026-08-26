-- 푸시 발송(FCM)의 주소록. 앱이 FCM 에서 받은 디바이스 토큰을 회원별로 저장한다.
-- 회원 1명이 여러 행을 가질 수 있다(폰·태블릿, 기기 교체).
-- 토큰 단독 유일 제약 — "한 토큰 = 한 회원". 공용 기기에서 다른 회원이 로그인하면
-- 행을 새로 만들지 않고 소유자를 갱신해, 이전 회원의 알림이 남의 폰에 뜨는 것을 막는다.
CREATE TABLE member_device
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL COMMENT '소유 회원 ID',
    token      VARCHAR(512) NOT NULL COMMENT 'FCM 등록 토큰 (불투명 문자열 — 형식을 해석하지 않는다)',
    platform   VARCHAR(10)  NOT NULL COMMENT '기기 플랫폼 (IOS, ANDROID)',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT member_device_uk_1 UNIQUE (token)
);

-- 발송이 회원의 기기 목록을 집어오는 경로. 탈퇴 정리도 이 인덱스를 탄다.
CREATE INDEX member_device_index_1 ON member_device (member_id);
