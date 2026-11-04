-- 첫 대화 힌트(QUIZ_HINT)는 서버가 만들어 보낸 사람이 없다 → sender_id NULL 허용 (IMP-1 BE-4).
-- 질문 카드·힌트는 문항·선택지·힌트 질문을 JSON 으로 실어 암호문이 4,100자를 넘을 수 있다 → 8,200자로 늘린다.
-- 둘 다 INPLACE 로 바뀐다(VARCHAR 길이 접두 바이트 2바이트 그대로, NULL 허용은 제자리 재구성). 복사가 필요하면 LOCK=NONE 이라 잠그는 대신 에러로 멈춘다.
-- 배포 전에 먼저 실행한다. 기존 엔티티(NOT NULL·4100)로도 NULL 행이 없으면 검증(ddl-auto=validate)을 통과한다.
ALTER TABLE chat_message
    MODIFY sender_id BIGINT NULL COMMENT '보낸 회원 ID (서버가 만든 첫 대화 힌트는 NULL)',
    MODIFY message_type VARCHAR(20) NOT NULL COMMENT '메시지 유형 (TEXT, IMAGE, SYSTEM, QUIZ_HINT, QUIZ_CARD)',
    MODIFY content VARCHAR(8200) NOT NULL COMMENT '메시지 내용 (암호화 저장)',
    ALGORITHM = INPLACE,
    LOCK = NONE;
