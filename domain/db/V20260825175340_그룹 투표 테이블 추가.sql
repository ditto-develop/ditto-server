-- 그룹 채팅방에서 만날 장소·시간을 정하는 투표(피그마 4.2.2~4.2.4).
--
-- 투표는 그룹 방에 종속된다 — 방이 없으면 존재할 수 없고 인가도 방 멤버십으로 판정하므로
-- 이름을 chat_vote 로 두어 채팅 도메인 안에 붙인다.
-- 선택지와 표를 각각 별도 테이블로 두는 이유: 선택지는 유형별 상한·중복 제약이 필요하고,
-- 표는 결과 화면이 "누가 무엇에 투표했는지"를 그대로 보여줘야 해서 카운트가 아니라 행으로 남긴다.
CREATE TABLE chat_vote
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    room_id        BIGINT      NOT NULL COMMENT '채팅방 ID',
    open_room_id   BIGINT      NULL COMMENT '열려 있는 동안만 room_id 사본. 마감하면 NULL (방당 열린 투표 1개 제약용)',
    created_by     BIGINT      NOT NULL COMMENT '투표를 만든 회원 ID',
    allow_multiple BOOLEAN     NOT NULL COMMENT '복수 선택 허용 여부 (생성 시 확정, 장소·시간 공통)',
    status         VARCHAR(20) NOT NULL COMMENT '상태 (OPEN, CLOSED)',
    closed_at      DATETIME(6) NULL COMMENT '마감 시각 (진행 중이면 NULL)',
    closed_reason  VARCHAR(20) NULL COMMENT '마감 사유 (MEMBER, ROOM_ENDED. 진행 중이면 NULL)',
    closed_by      BIGINT      NULL COMMENT '마감한 회원 ID (멤버 마감일 때만 값)',
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    -- 방당 열린 투표는 하나다("투표 생성 후 바텀시트에서 '투표 만들기' 삭제", 피그마 4.2.3).
    -- MySQL 유일 인덱스는 NULL 중복을 허용하므로, 마감하며 open_room_id 를 비우면
    -- 닫힌 투표는 몇 개든 남고 열린 투표만 방당 하나로 묶인다.
    -- 생성 경로는 방 행 잠금으로도 막지만(ADR 0011), 잠금을 빠뜨린 경로가 생겨도 DB 가 마지막으로 막는다.
    CONSTRAINT chat_vote_uk_1 UNIQUE (open_room_id),
    PRIMARY KEY (id)
) COMMENT '그룹 채팅 만남 투표';

-- 방의 투표를 최신순으로 읽는 경로 (방 진입·재접속 복구).
CREATE INDEX chat_vote_index_1 ON chat_vote (room_id, id);

CREATE TABLE chat_vote_option
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    vote_id     BIGINT       NOT NULL COMMENT '투표 ID',
    option_type VARCHAR(20)  NOT NULL COMMENT '선택지 유형 (PLACE, TIME)',
    label       VARCHAR(100) NULL COMMENT 'PLACE 상호명 (TIME 은 NULL — 표시 문구는 meet_at 으로 클라이언트가 만든다)',
    address     VARCHAR(200) NULL COMMENT 'PLACE 도로명 주소',
    map_link    VARCHAR(500) NULL COMMENT 'PLACE 카카오맵 URL',
    latitude    DOUBLE       NULL COMMENT 'PLACE 위도',
    longitude   DOUBLE       NULL COMMENT 'PLACE 경도',
    meet_at     DATETIME(6)  NULL COMMENT 'TIME 만날 일시 (분 단위, 초 이하 0)',
    created_by  BIGINT       NOT NULL COMMENT '선택지를 만든 회원 ID',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    -- 같은 투표에 같은 장소를 두 번 넣지 못하게 한다. TIME 은 label 이 NULL 이라 이 제약에 걸리지 않는다.
    CONSTRAINT chat_vote_option_uk_1 UNIQUE (vote_id, option_type, label),
    -- 시간의 중복 판정은 표시 문구가 아니라 실제 일시로 한다. PLACE 는 meet_at 이 NULL 이라 걸리지 않는다.
    CONSTRAINT chat_vote_option_uk_2 UNIQUE (vote_id, meet_at),
    PRIMARY KEY (id)
) COMMENT '투표 선택지 (장소·시간)';

-- 상세·결과 조회가 선택지를 유형별로 입력 순으로 읽는 경로.
-- id 오름차순이 곧 입력 순이라 정렬 컬럼을 따로 두지 않는다
-- (동표일 때 "입력 순으로 노출"하는 화면 규칙이 이 순서를 그대로 쓴다).
CREATE INDEX chat_vote_option_index_1 ON chat_vote_option (vote_id, option_type, id);

CREATE TABLE chat_vote_choice
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    vote_id    BIGINT      NOT NULL COMMENT '투표 ID (선택지를 거치지 않고 내 표·집계를 읽으려고 중복 보관)',
    option_id  BIGINT      NOT NULL COMMENT '선택지 ID',
    member_id  BIGINT      NOT NULL COMMENT '투표한 회원 ID',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    -- 한 회원이 같은 선택지에 두 표를 던질 수 없다.
    CONSTRAINT chat_vote_choice_uk_1 UNIQUE (option_id, member_id),
    PRIMARY KEY (id)
) COMMENT '투표에 던진 표 (누가 어느 선택지를 골랐는지)';

-- 상세 조회(집계 + 내 표)와 재투표 치환(차집합 계산)이 한 투표의 표를 한 번에 읽는 경로.
-- 방 인원이 3~4명이라 표는 투표당 수십 행이고, 유형별 분류·선택지별 집계는 메모리에서 한다.
CREATE INDEX chat_vote_choice_index_1 ON chat_vote_choice (vote_id, member_id);
