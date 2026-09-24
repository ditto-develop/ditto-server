-- 어드민이 보낸 시스템 공지의 이력. notification 행은 수신자별 기록이라 "언제 누가 무엇을 몇 명에게
-- 보냈나"를 볼 수 없고 30일 뒤 purge 되므로 따로 둔다. 발송은 어드민 요청 안에서 동기로 끝나고,
-- 실제 적재된 수를 recipient_count 에 남긴다. 실패한 회원은 로그로만 남는다(다른 알림과 같다).
CREATE TABLE system_notice
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    title            VARCHAR(100) NOT NULL COMMENT '제목 (notification.title 과 같은 제한)',
    body             VARCHAR(500) NULL COMMENT '본문 (notification.body 와 같은 제한)',
    author_member_id BIGINT       NOT NULL COMMENT '발송한 어드민 회원 ID',
    author_name      VARCHAR(50)  NULL COMMENT '발송자 이름 (발송 시점 스냅샷)',
    author_email     VARCHAR(100) NULL COMMENT '발송자 이메일 (발송 시점 스냅샷)',
    recipient_count  INT          NOT NULL COMMENT '적재된 알림 수',
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);
