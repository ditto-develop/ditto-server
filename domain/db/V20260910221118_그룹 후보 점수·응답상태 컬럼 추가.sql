-- 그룹 매칭을 선착순 방 배정에서 "배치가 짠 후보 그룹에 수락/거절"로 바꾸기 위한 컬럼 추가.
--
-- group_match 는 이제 후보 그룹으로 먼저 만들어진다(is_active = false). 멤버는 후보로 미리 깔리고
-- (status = PENDING) 수락한 사람만 ACCEPTED 가 된다. participant_count 는 그래서 "참가자 수"가 아니라
-- "수락자 수"를 뜻하게 되고, 3명에 도달하면 is_active 가 켜지며 채팅방이 열린다.
--
-- 두 테이블 모두 행이 없어(선착순 그룹이 실제로 운영된 적 없음) 기본값 없이 NOT NULL 로 추가한다.

ALTER TABLE group_match
    ADD COLUMN score DOUBLE NOT NULL COMMENT '그룹 점수 (구성원 모든 페어 일치율의 평균, 0.0~100.0)';

ALTER TABLE group_match_member
    ADD COLUMN status VARCHAR(20) NOT NULL COMMENT '후보 응답 상태 (PENDING, ACCEPTED, DECLINED)';

CREATE INDEX group_match_member_index_3 ON group_match_member (member_id, status);
