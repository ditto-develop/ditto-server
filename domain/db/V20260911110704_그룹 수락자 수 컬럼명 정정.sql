-- group_match.participant_count 는 선착순 배정 시절 "방에 들어온 참가자 수"였다.
-- 후보 그룹 방식으로 바뀌며 뜻이 "수락자 수"로 달라졌는데 이름이 그대로였다
-- (API 응답은 이미 acceptedCount 를 쓴다). 데이터가 없는 지금이 리네임 비용이 가장 싸다.

ALTER TABLE group_match
    CHANGE COLUMN participant_count accepted_count INT NOT NULL DEFAULT 0 COMMENT '수락자 수';
