-- 채팅 기반 테이블 3종.
-- chat_room: 매칭(원본)당 채팅방 1개. room_type + source_id 로 원본을 가리킨다 (1:1=personal_match, 그룹=group_match).
-- chat_room_member: 방 참여자 + 회원별 마지막 읽은 메시지 위치(last_read_message_id).
-- chat_message: 방 메시지. id(단조 증가)로 정렬·커서 페이징한다.
CREATE TABLE chat_room
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    room_type  VARCHAR(20) NOT NULL COMMENT '채팅방 유형 (PERSONAL, GROUP)',
    source_id  BIGINT      NOT NULL COMMENT '원본 매칭 ID (personal_match 또는 group_match 의 ID)',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chat_room_uk_1 UNIQUE (room_type, source_id)
);

CREATE TABLE chat_room_member
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    room_id              BIGINT      NOT NULL COMMENT '채팅방 ID',
    member_id            BIGINT      NOT NULL COMMENT '회원 ID',
    last_read_message_id BIGINT      NULL COMMENT '마지막으로 읽은 메시지 ID',
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chat_room_member_uk_1 UNIQUE (room_id, member_id)
);

CREATE INDEX chat_room_member_index_1 ON chat_room_member (member_id);

CREATE TABLE chat_message
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    room_id      BIGINT        NOT NULL COMMENT '채팅방 ID',
    sender_id    BIGINT        NOT NULL COMMENT '보낸 회원 ID',
    message_type VARCHAR(20)   NOT NULL COMMENT '메시지 유형 (TEXT, IMAGE, SYSTEM)',
    content      VARCHAR(1000) NOT NULL COMMENT '메시지 내용',
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX chat_message_index_1 ON chat_message (room_id, id);
