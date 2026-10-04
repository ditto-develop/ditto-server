-- quiz_set_id 하나로 찾는 경로가 기존 인덱스(회원 ID가 맨 앞)를 타지 못해 테이블을 끝까지 읽는다.
-- 어드민 퀴즈셋 상세·삭제의 매칭 기록 검사(exists)와, 매칭 배치가 후보 없는 셋을 고르는 조인이 이 경로다.
-- group_match 는 group_match_index_1 (quiz_set_id, is_active)이 있어 따로 두지 않는다.
CREATE INDEX match_candidate_index_2 ON match_candidate (quiz_set_id);
CREATE INDEX personal_match_index_3 ON personal_match (quiz_set_id);
