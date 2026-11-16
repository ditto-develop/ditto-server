-- 공개를 마쳐 enum 에서 지운 기능 플래그(QUIZ_CHAT, RECORDS)의 행을 지운다. 배경: docs/adr/0051 "폐기".
-- 정리용이다 — 서버는 지금 enum 에 있는 키의 행만 읽어(findAllByKeyIn) 이 행이 남아 있어도 동작한다.
DELETE FROM feature_flag WHERE flag_key IN ('QUIZ_CHAT', 'RECORDS');
