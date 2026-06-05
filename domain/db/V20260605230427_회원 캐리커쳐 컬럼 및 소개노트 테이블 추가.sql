-- member: 프로필 캐리커쳐 컬럼 추가 (FE에서 받은 문자열 그대로 저장)
ALTER TABLE member
    ADD COLUMN caricature VARCHAR(100) NULL COMMENT '프로필 캐리커쳐 (FE에서 받은 문자열 그대로 저장)' AFTER job;

-- intro_note 테이블
-- 회원은 고정 질문(IntroQuestion)당 답변 1개를 가진다. 같은 질문 재저장은 갱신(upsert).
CREATE TABLE intro_note
(
    id         BIGINT       AUTO_INCREMENT NOT NULL COMMENT '소개노트 ID',
    member_id  BIGINT       NOT NULL COMMENT '회원 ID',
    question   VARCHAR(30)  NOT NULL COMMENT '소개노트 질문 (enum 이름)',
    answer     VARCHAR(500) NOT NULL COMMENT '답변 (빈 문자열 허용 — 부분 저장)',
    created_at DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at DATETIME(6)  NOT NULL COMMENT '수정일시',
    CONSTRAINT pk_intro_note PRIMARY KEY (id),
    CONSTRAINT intro_note_uk_1 UNIQUE (member_id, question)
);

CREATE INDEX intro_note_index_1 ON intro_note (member_id);
