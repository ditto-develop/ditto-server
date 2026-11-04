-- 문항별 '대화 힌트 질문' (IMP-1 BE-7). 어드민 퀴즈 폼에서 선택 입력. 첫 대화 힌트·질문 카드에 실린다.
ALTER TABLE quiz
    ADD COLUMN hint_question VARCHAR(200) NULL COMMENT '대화 힌트 질문 (어드민 입력, 선택)' AFTER display_order;
