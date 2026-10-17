-- 본문을 암호화해 저장하면 1000자 한글이 4,047자가 된다. 입력 상한 1000자는 그대로다.
-- 길이 바이트 수가 그대로라 테이블 복사 없이 바뀐다. 복사가 필요하면 잠그지 않고 실패한다.
-- 배포 전에 먼저 실행한다. 새 코드가 좁은 컬럼에 암호문을 쓰면 긴 메시지 전송이 실패한다.
ALTER TABLE chat_message
    MODIFY content VARCHAR(4100) NOT NULL COMMENT '메시지 내용 (암호화 저장)',
    ALGORITHM = INPLACE,
    LOCK = NONE;
