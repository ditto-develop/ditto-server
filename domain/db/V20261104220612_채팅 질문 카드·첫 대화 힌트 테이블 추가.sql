-- 질문 카드(IMP-1 BE-5): 방에 같은 문항은 한 번만 올린다 → UK(room_id, quiz_id). 카드 내용은 message_id 의 메시지에 있다.
-- 첫 대화 힌트(IMP-1 BE-4): 방당 1개 → UK(room_id). 고를 문항이 없던 방도 message_id 없이 행을 남겨 누락 복구가 다시 집지 않게 한다.
CREATE TABLE chat_quiz_card
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    room_id    BIGINT      NOT NULL COMMENT '채팅방 ID',
    quiz_id    BIGINT      NOT NULL COMMENT '퀴즈 ID',
    message_id BIGINT      NOT NULL COMMENT '카드 메시지 ID (chat_message.id)',
    sender_id  BIGINT      NOT NULL COMMENT '보낸 회원 ID',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chat_quiz_card_uk_1 UNIQUE (room_id, quiz_id)
);

CREATE TABLE chat_quiz_hint
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    room_id    BIGINT      NOT NULL COMMENT '채팅방 ID',
    message_id BIGINT      NULL COMMENT '힌트 메시지 ID (고를 문항이 없었으면 NULL)',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chat_quiz_hint_uk_1 UNIQUE (room_id)
);
