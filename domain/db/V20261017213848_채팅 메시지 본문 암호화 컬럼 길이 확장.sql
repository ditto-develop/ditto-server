-- 본문을 암호화해 저장하면 1000자 한글이 4,047자가 된다. 입력 상한 1000자는 그대로다.
-- VARCHAR 길이 접두 바이트(2바이트)가 그대로라 INPLACE 로 바뀐다. 복사가 필요하면 LOCK=NONE 이라 잠그는 대신 에러로 멈춘다.
-- 배포 전에 먼저 실행한다. 배포 순서와 확인 방법은 docs/adr/0043 에 있다.
ALTER TABLE chat_message
    MODIFY content VARCHAR(4100) NOT NULL COMMENT '메시지 내용 (암호화 저장)',
    ALGORITHM = INPLACE,
    LOCK = NONE;
