-- match_request 테이블
-- member_id_1 = min(requester, receiver), member_id_2 = max(requester, receiver) 로 정규화
-- → A→B 요청과 B→A 요청이 동일한 UK를 공유하므로 방향 무관 중복 방지
CREATE TABLE personal_match
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
    CONSTRAINT pk_personal_match PRIMARY KEY (id),
    CONSTRAINT personal_match_uk_1 UNIQUE (member_id_1, member_id_2, quiz_set_id)
);

CREATE INDEX personal_match_index_1 ON personal_match (member_id_1, quiz_set_id, status);
CREATE INDEX personal_match_index_2 ON personal_match (member_id_2, quiz_set_id, status);

-- group_match_room 테이블
-- 퀴즈셋 1개에서 여러 소규모 그룹 생성 가능 (quizSetId UK 없음)
CREATE TABLE group_match
(
    id                BIGINT      AUTO_INCREMENT NOT NULL COMMENT '그룹 매칭 방 ID',
    quiz_set_id       BIGINT      NOT NULL COMMENT '퀴즈 세트 ID',
    is_active         BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '활성화 여부 (참가자 3명 이상)',
    participant_count INT         NOT NULL DEFAULT 0 COMMENT '참가자 수',
    created_at        DATETIME(6) NOT NULL COMMENT '생성일시',
    updated_at        DATETIME(6) NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_group_match PRIMARY KEY (id)
);

CREATE INDEX group_match_index_1 ON group_match (quiz_set_id, is_active);

-- group_match_room_member 테이블
-- 같은 방에 같은 멤버 중복 입장 불가
-- 한 멤버가 동일 퀴즈셋의 여러 방에 참여하는 것은 허용
CREATE TABLE group_match_member
(
    id         BIGINT      AUTO_INCREMENT NOT NULL COMMENT '방 멤버 ID',
    room_id    BIGINT      NOT NULL COMMENT '그룹 매칭 방 ID',
    member_id  BIGINT      NOT NULL COMMENT '회원 ID',
    created_at DATETIME(6) NOT NULL COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_group_match_member PRIMARY KEY (id),
    CONSTRAINT group_match_member_uk_1 UNIQUE (room_id, member_id)
);

CREATE INDEX group_match_member_index_1 ON group_match_member (room_id);
CREATE INDEX group_match_member_index_2 ON group_match_member (member_id);

-- group_match_decline 테이블
-- 한 멤버는 동일 퀴즈셋에서 한 번만 거절 가능
CREATE TABLE group_match_decline
(
    id          BIGINT      AUTO_INCREMENT NOT NULL COMMENT '거절 ID',
    quiz_set_id BIGINT      NOT NULL COMMENT '퀴즈 세트 ID',
    member_id   BIGINT      NOT NULL COMMENT '회원 ID',
    created_at  DATETIME(6) NOT NULL COMMENT '생성일시',
    updated_at  DATETIME(6) NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_group_match_decline PRIMARY KEY (id),
    CONSTRAINT group_match_decline_uk_1 UNIQUE (quiz_set_id, member_id)
);
