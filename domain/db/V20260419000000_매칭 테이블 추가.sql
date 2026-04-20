-- match_request 테이블
-- member_id_1 = min(requester, receiver), member_id_2 = max(requester, receiver) 로 정규화
-- → A→B 요청과 B→A 요청이 동일한 UK를 공유하므로 방향 무관 중복 방지
CREATE TABLE match_request
(
    id           BIGINT AUTO_INCREMENT NOT NULL COMMENT '매칭 요청 ID',
    member_id_1  BIGINT      NOT NULL COMMENT '페어 중 작은 회원 ID (정규화)',
    member_id_2  BIGINT      NOT NULL COMMENT '페어 중 큰 회원 ID (정규화)',
    requester_id BIGINT      NOT NULL COMMENT '요청 보낸 회원 ID',
    quiz_set_id  BIGINT      NOT NULL COMMENT '퀴즈 세트 ID',
    status       VARCHAR(20) NOT NULL COMMENT '매칭 요청 상태',
    responded_at DATETIME(6)          COMMENT '응답일시',
    created_at   DATETIME(6) NOT NULL COMMENT '생성일시',
    updated_at   DATETIME(6) NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_match_request PRIMARY KEY (id),
    CONSTRAINT match_request_uk_1 UNIQUE (member_id_1, member_id_2, quiz_set_id)
);

CREATE INDEX match_request_index_1 ON match_request (member_id_1, quiz_set_id, status);
CREATE INDEX match_request_index_2 ON match_request (member_id_2, quiz_set_id, status);

-- group_match_room 테이블
-- quizSetId UK 없음 — 퀴즈셋 1개에서 여러 소규모 그룹 생성 가능
-- 멤버당 1개 참여 보장은 group_match_participant.(quiz_set_id, member_id) UK로 처리
CREATE TABLE group_match_room
(
    id                BIGINT      AUTO_INCREMENT NOT NULL COMMENT '그룹 매칭 방 ID',
    quiz_set_id       BIGINT      NOT NULL COMMENT '퀴즈 세트 ID',
    is_active         BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '활성화 여부 (참가자 3명 이상)',
    participant_count INT         NOT NULL DEFAULT 0 COMMENT '참가자 수',
    created_at        DATETIME(6) NOT NULL COMMENT '생성일시',
    updated_at        DATETIME(6) NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_group_match_room PRIMARY KEY (id)
);

CREATE INDEX group_match_room_index_1 ON group_match_room (quiz_set_id, is_active);

-- group_match_participant 테이블
-- (quiz_set_id, member_id) UK — 한 멤버는 동일 퀴즈셋에서 하나의 그룹에만 참여 가능
CREATE TABLE group_match_participant
(
    id         BIGINT      AUTO_INCREMENT NOT NULL COMMENT '참가자 ID',
    quiz_set_id BIGINT     NOT NULL COMMENT '퀴즈 세트 ID',
    member_id  BIGINT      NOT NULL COMMENT '회원 ID',
    status     VARCHAR(20) NOT NULL COMMENT '참여 상태',
    room_id    BIGINT               COMMENT '그룹 매칭 방 ID (JOINED인 경우에만 존재)',
    created_at DATETIME(6) NOT NULL COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_group_match_participant PRIMARY KEY (id),
    CONSTRAINT group_match_participant_uk_1 UNIQUE (quiz_set_id, member_id)
);

CREATE INDEX group_match_participant_index_1 ON group_match_participant (quiz_set_id, member_id);
CREATE INDEX group_match_participant_index_2 ON group_match_participant (room_id);
