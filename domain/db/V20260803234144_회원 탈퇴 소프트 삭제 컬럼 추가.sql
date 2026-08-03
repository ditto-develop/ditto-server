-- 탈퇴를 hard delete에서 소프트 삭제로 전환한다.
-- 탈퇴 화면(피그마 6.2.4)이 "탈퇴 후 30일 이내 재가입하면 계정을 복구할 수 있습니다 /
-- 30일이 지나면 모든 데이터가 완전히 삭제됩니다"를 안내하므로, 즉시 삭제로는 그 약속을 지킬 수 없다.
--
-- status에 LEFT 값이 추가되지만 VARCHAR 저장이라 컬럼 변경은 없다.
-- social_account는 탈퇴 시에도 보존한다 — 재가입 시 같은 회원을 찾아내는 유일한 근거이며(복구),
-- 동시에 제재 회피용 재가입을 막는 근거이기도 하다.
ALTER TABLE member
    ADD COLUMN left_at DATETIME(6) NULL COMMENT '탈퇴 일시 (LEFT일 때만 값 존재)';

ALTER TABLE member
    ADD COLUMN leave_reason VARCHAR(50) NULL COMMENT '탈퇴 사유 code (탈퇴 화면 선택값)';

-- 30일 경과 회원을 찾는 삭제 배치의 조회 조건.
CREATE INDEX member_index_3 ON member (status, left_at);
