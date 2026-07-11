-- member_report 테이블
-- 회원이 상대 회원을 신고한다. 접수 후 상태 전이는 어드민 검토에서만 일어난다 (RECEIVED → ACTIONED | REJECTED | REJECTED_ABUSIVE).
CREATE TABLE member_report
(
    id                 BIGINT       AUTO_INCREMENT NOT NULL COMMENT '신고 ID',
    reporter_id        BIGINT       NOT NULL COMMENT '신고자 회원 ID',
    reported_member_id BIGINT       NOT NULL COMMENT '피신고자 회원 ID',
    reason             VARCHAR(30)  NOT NULL COMMENT '신고 사유 (enum 이름)',
    source             VARCHAR(20)  NOT NULL COMMENT '신고 접수 위치 (enum 이름)',
    detail             VARCHAR(500) NULL COMMENT '상세 설명 (기타 사유는 필수)',
    status             VARCHAR(30)  NOT NULL COMMENT '신고 처리 상태 (enum 이름)',
    created_at         DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at         DATETIME(6)  NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_member_report PRIMARY KEY (id)
);

CREATE INDEX member_report_index_1 ON member_report (status, created_at);
CREATE INDEX member_report_index_2 ON member_report (reported_member_id);
CREATE INDEX member_report_index_3 ON member_report (reporter_id);

-- member_report_image 테이블
-- 신고당 최대 3장의 첨부 이미지를 가진다. object_key는 S3 비공개 버킷의 객체 키.
CREATE TABLE member_report_image
(
    id               BIGINT       AUTO_INCREMENT NOT NULL COMMENT '신고 첨부 이미지 ID',
    member_report_id BIGINT       NOT NULL COMMENT '신고 ID',
    object_key       VARCHAR(200) NOT NULL COMMENT 'S3 객체 키',
    display_order    INT          NOT NULL COMMENT '첨부 순서 (0부터)',
    created_at       DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at       DATETIME(6)  NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_member_report_image PRIMARY KEY (id),
    CONSTRAINT member_report_image_uk_1 UNIQUE (member_report_id, display_order)
);
