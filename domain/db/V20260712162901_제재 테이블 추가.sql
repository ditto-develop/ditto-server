-- sanction 테이블
-- 제재 이력의 SSOT — 차수·기간·조치자의 진실은 이 테이블이 갖고, member.status는 매 요청 집행용 반영값이다 (ADR 0009).
CREATE TABLE sanction
(
    id               BIGINT       AUTO_INCREMENT NOT NULL COMMENT '제재 ID',
    member_id        BIGINT       NOT NULL COMMENT '피제재 회원 ID',
    member_report_id BIGINT       NULL COMMENT '근거 신고 ID (어드민 직권 제재는 NULL)',
    origin           VARCHAR(20)  NOT NULL COMMENT '제재 발생 경위 (enum 이름)',
    level            VARCHAR(20)  NOT NULL COMMENT '제재 수위 (enum 이름)',
    starts_at        DATETIME(6)  NOT NULL COMMENT '제재 시작 일시',
    ends_at          DATETIME(6)  NULL COMMENT '제재 종료 일시 (영구 차단은 NULL)',
    status           VARCHAR(20)  NOT NULL COMMENT '제재 상태 (enum 이름)',
    created_by       BIGINT       NOT NULL COMMENT '조치자 회원 ID',
    creator_name     VARCHAR(50)  NOT NULL COMMENT '조치자 표시명 스냅샷 (계정 삭제 후에도 감사 기록 보존)',
    note             VARCHAR(500) NULL COMMENT '조치 메모',
    created_at       DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at       DATETIME(6)  NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_sanction PRIMARY KEY (id)
);

CREATE INDEX sanction_index_1 ON sanction (member_id, status);
-- 만료 일괄 종결 배치 쿼리용
CREATE INDEX sanction_index_2 ON sanction (status, ends_at);
