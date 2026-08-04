-- chat_room.room_type -> source_type. source_id 와 함께 "원본(매칭)을 가리키는 다형 FK" 한 쌍이므로
-- 접두어를 맞춘다. room_type 은 방을, source_id 는 원본을 가리켜 짝이 어긋나 있었다. 배경: ADR 0015.
--
-- ⚠️ 적용 순서: 이 rename 은 롤링 배포와 호환되지 않는다. 컬럼이 하나뿐이라 구버전(room_type)과
-- 신버전(source_type) 중 한쪽은 반드시 깨진다. **ALTER 실행 직후 곧바로 배포**해야 하며,
-- 그 사이 구버전은 Hibernate 스키마 검증(ddl-auto: validate)에 실패해 기동하지 못한다.
-- 출시 전이라 이 짧은 중단을 감수하는 선택이다(ADR 0015 의 Consequences 참조).
--
-- MySQL 은 CHANGE 로 컬럼명을 바꾸면 그 컬럼을 쓰는 인덱스 정의도 함께 갱신하므로,
-- chat_room_uk_1(room_type, source_id) 는 별도로 재생성하지 않는다.
ALTER TABLE chat_room
    CHANGE COLUMN room_type source_type VARCHAR(20) NOT NULL COMMENT '원본 유형 (PERSONAL, GROUP)';
