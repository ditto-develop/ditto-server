-- 어드민이 보낸 시스템 공지의 이력. notification 행은 수신자별 기록이라 "언제 누가 무엇을 몇 명에게
-- 보냈나"를 볼 수 없고 30일 뒤 purge 되므로 따로 둔다. 발송은 어드민 요청 안에서 동기로 끝난다.
-- 행은 발송 시작 시 대상 수(target_count)와 함께 생기고, 끝나면 실제 적재된 수(recipient_count)가 채워진다.
-- recipient_count 가 NULL 이면 아직 발송 중이다. 발송이 길어져 화면이 먼저 돌아와도 "0명"이 아니라
-- "발송 중"으로 보여야 어드민이 다시 보내지 않는다(SYSTEM_NOTICE 는 중복을 막지 않는다).
-- 실패한 회원은 로그로만 남는다(다른 알림과 같다). 발송 도중 서버가 멈추면 NULL 로 남는다 — 상태 컬럼은 두지 않는다.
CREATE TABLE system_notice
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    title            VARCHAR(100) NOT NULL COMMENT '제목 (notification.title 과 같은 제한)',
    body             VARCHAR(500) NULL COMMENT '본문 (notification.body 와 같은 제한)',
    author_member_id BIGINT       NOT NULL COMMENT '발송한 어드민 회원 ID',
    author_name      VARCHAR(50)  NULL COMMENT '발송자 이름 (발송 시점 스냅샷)',
    author_email     VARCHAR(100) NULL COMMENT '발송자 이메일 (발송 시점 스냅샷)',
    target_count     INT          NOT NULL COMMENT '발송 시작 시 대상(활성 회원) 수',
    recipient_count  INT          NULL COMMENT '적재된 알림 수 (발송 중이면 NULL)',
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);
