-- ChatRoomType 에 REMATCH 가 추가됐다 — 그룹 재매칭으로 성사된 두 사람의 1:1 방(원본은 rematch.id).
--
-- source_type 은 VARCHAR(20) + @Enumerated(STRING) 이고 CHECK 제약이 없어 값 추가 자체에는 DDL 이 필요 없다.
-- 이 마이그레이션은 주석만 맞춘다 — 스키마를 읽는 사람이 유형 목록을 이 주석에서 확인하기 때문이다.
-- source_id 도 함께 고친다: 가리키는 원본 테이블이 personal_match / group_match / rematch 셋으로 늘었다.
ALTER TABLE chat_room
    MODIFY COLUMN source_type VARCHAR(20) NOT NULL COMMENT '원본 유형 (PERSONAL, GROUP, REMATCH)',
    MODIFY COLUMN source_id BIGINT NOT NULL COMMENT '원본 매칭 ID (personal_match, group_match 또는 rematch 의 ID)';
