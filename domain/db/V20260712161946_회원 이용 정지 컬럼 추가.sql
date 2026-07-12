-- member: 제재 상태 확장 (MemberStatus에 SUSPENDED/BANNED 추가 — enum은 VARCHAR 저장이라 컬럼만 추가)
ALTER TABLE member
    ADD COLUMN suspended_until DATETIME(6) NULL COMMENT '이용 정지 해제 예정 일시 (SUSPENDED일 때만 값 존재)' AFTER status;
