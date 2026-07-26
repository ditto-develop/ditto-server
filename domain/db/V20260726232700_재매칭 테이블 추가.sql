-- rematch 테이블
-- 그룹 채팅 종료 시 실제 참여자의 모든 비순서 쌍을 미리 만들어, 단방향 재매칭 의사와 상호 성사 결과를 비공개로 보관한다.
-- 기획 용어 "그룹 재매칭"의 코드 명명은 rematch 다 — 출처(그룹)는 source_group_match_id 가 담는다.
-- 쌍은 member_id_1 = min, member_id_2 = max 로 정규화한다 (ADR 0008 패턴).
CREATE TABLE rematch
(
    id                    BIGINT      AUTO_INCREMENT NOT NULL COMMENT '재매칭 ID',
    source_group_match_id BIGINT      NOT NULL COMMENT '원본 그룹 매칭 ID',
    source_chat_room_id   BIGINT      NOT NULL COMMENT '종료된 그룹 채팅방 ID',
    quiz_set_id           BIGINT      NOT NULL COMMENT '원본 퀴즈 세트 ID',
    week_started_on       DATE        NOT NULL COMMENT '주간 제한 키 (Asia/Seoul 기준 해당 주 월요일)',
    member_id_1           BIGINT      NOT NULL COMMENT '페어 중 작은 회원 ID (정규화)',
    member_id_2           BIGINT      NOT NULL COMMENT '페어 중 큰 회원 ID (정규화)',
    member_1_wants        TINYINT(1)  NULL COMMENT 'member_id_1의 재매칭 선택 (NULL=미응답)',
    member_2_wants        TINYINT(1)  NULL COMMENT 'member_id_2의 재매칭 선택 (NULL=미응답)',
    status                VARCHAR(20) NOT NULL COMMENT '재매칭 상태 (enum 이름)',
    matched_at            DATETIME(6) NULL COMMENT '상호 선택 성사 일시',
    created_at            DATETIME(6) NOT NULL COMMENT '생성일시',
    updated_at            DATETIME(6) NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_rematch PRIMARY KEY (id),
    -- 같은 소스 그룹에서 같은 두 멤버의 쌍은 방향 무관 유일
    CONSTRAINT rematch_uk_1 UNIQUE (source_group_match_id, member_id_1, member_id_2)
);
