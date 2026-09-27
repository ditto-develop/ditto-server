-- 신고 사유를 여러 개 고를 수 있게 한다. 선택한 사유 전부는 reasons(콤마 구분 enum 이름)에,
-- 대표 사유(가장 심각한 것)는 기존 reason 에 둔다 — 제재 안내(내 제재 조회)와 기존 조회가 한 값을 계속 읽는다.
-- 관심사(member.interests)와 같은 저장 방식이다. 사유 값이 다섯 개뿐이고 사유로 조인·필터하는 조회가 없다.
ALTER TABLE member_report
    ADD COLUMN reasons VARCHAR(200) NOT NULL DEFAULT '' COMMENT '선택한 신고 사유 전부 (콤마 구분 enum 이름)' AFTER reason;

UPDATE member_report
SET reasons = reason
WHERE reasons = '';
