-- 애플 계정 삭제·연결 해제 알림이 왔는데 진행 중인 매칭·채팅이 있으면 탈퇴를 미뤄 둔다.
-- 값이 있으면 대기 중이고, 스케줄러가 진행이 끝난 뒤 이 사유로 탈퇴시킨다.
ALTER TABLE member
    ADD COLUMN deferred_leave_reason VARCHAR(50) NULL COMMENT '미뤄 둔 탈퇴 사유 code (대기 중이 아니면 NULL)' AFTER leave_reason_detail;
