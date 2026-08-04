-- 설정 화면(피그마 6.2)용 테이블 2종.
-- member_notification_setting: 알림 토글 3종. member에 컬럼을 늘리지 않고 1:1 테이블로 뺀다
--                              — 항목이 늘어날 여지가 크고, 회원 조회 경로마다 따라다닐 값이 아니다.
--                              행이 없으면 기본값으로 간주하므로 가입 시 미리 만들지 않는다.
-- member_block: 사용자 간 차단. 제재(sanction)와 무관하다 — 제재는 계정 전체에 대한 운영 조치이고
--               차단은 (차단한 사람, 차단된 사람) 쌍에만 효력이 있으며 당사자가 언제든 해제한다.
CREATE TABLE member_notification_setting
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL COMMENT '회원 ID',
    matching   BOOLEAN     NOT NULL DEFAULT TRUE COMMENT '매칭 알림 수신 여부',
    chat       BOOLEAN     NOT NULL DEFAULT TRUE COMMENT '채팅 알림 수신 여부',
    marketing  BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '마케팅 정보 수신 여부',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT member_notification_setting_uk_1 UNIQUE (member_id)
);

CREATE TABLE member_block
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    blocker_id        BIGINT      NOT NULL COMMENT '차단한 회원 ID',
    blocked_member_id BIGINT      NOT NULL COMMENT '차단된 회원 ID',
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 같은 상대를 두 번 차단할 수 없다. 신고 시 차단이 재요청돼도 멱등하게 처리된다.
    CONSTRAINT member_block_uk_1 UNIQUE (blocker_id, blocked_member_id)
);

-- 차단 목록 조회는 최신순이고, 매칭 후보 제외도 blocker 기준으로 읽는다.
CREATE INDEX member_block_index_1 ON member_block (blocker_id, created_at);
-- 상대가 나를 차단했는지(프로필 조회 차단) 판정하는 역방향 조회.
CREATE INDEX member_block_index_2 ON member_block (blocked_member_id, blocker_id);
