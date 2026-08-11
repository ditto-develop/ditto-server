-- 알림 센터(피그마 7.2)용 테이블.
-- 수신자 1명당 1행이다 — 같은 사건이라도 받는 사람마다 문구가 다르고(닉네임), 읽음도 사람마다 따로 관리된다.
-- 문구(title·body)를 만들어 저장한다. 조회 때 다시 렌더하지 않는다 — 곧 붙을 푸시와 같은 문구여야 하고,
-- 센터는 "그때 무엇을 알렸는가"의 기록이라 닉네임이 바뀐 뒤 다시 렌더하면 사실이 달라진다.
-- target_id 가 무엇을 가리키는지는 type 이 정한다(예: CHAT_MESSAGE → chat_room.id).
-- 보관은 30일이다. 화면이 최근 30일만 보여주므로(스펙 표) 그 뒤의 행은 남길 이유가 없고,
-- 본문에 닉네임·메시지 미리보기가 들어가므로 오래 들고 있지 않는 편이 낫다.
CREATE TABLE notification
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL COMMENT '수신 회원 ID',
    type       VARCHAR(30)  NOT NULL COMMENT '알림 유형 (MATCH_RESULT, GROUP_FORMED, REMATCH_MATCHED, REVIEW_REQUEST, CHAT_MESSAGE, CHAT_ENDING_SOON, SYSTEM_NOTICE)',
    title      VARCHAR(100) NOT NULL COMMENT '제목 (발송 시점에 확정된 문구)',
    body       VARCHAR(500) NULL COMMENT '본문 (발송 시점에 확정된 문구)',
    target_id  BIGINT       NULL COMMENT '이동 대상 ID. 무엇을 가리키는지는 type 이 정한다',
    read_at    DATETIME(6)  NULL COMMENT '읽은 시각 (안읽음이면 NULL)',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

-- 목록 조회는 내 알림을 id DESC 로 커서 페이징한다.
CREATE INDEX notification_index_1 ON notification (member_id, id);
-- 미읽음 수(홈 배지)와 전체 읽음이 읽는 경로.
CREATE INDEX notification_index_2 ON notification (member_id, read_at);
-- 같은 사건을 두 번 알리지 않기 위한 존재 검사, 그리고 새 메시지 알림 접기(같은 방의 안읽은 행 제거).
-- 유일 제약으로 두지 않는다 — 새 메시지는 읽은 뒤 다시 오면 새 행이 되어야 한다.
CREATE INDEX notification_index_3 ON notification (member_id, type, target_id);
