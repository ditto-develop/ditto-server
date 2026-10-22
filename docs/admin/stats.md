# 어드민 운영 지표 (`/admin/stats`)

기획 "디토 어드민 운영 지표 정의서"의 지표를 회차·회원 조건으로 본다. 구조(서버는 집계 결과만, 계산은 JS)는 [ADR 0046](../adr/0046-admin-stats-render-in-browser.md).

## 공통

- **관리자 제외**: `role = ADMIN` 은 모든 지표에서 뺀다.
- **기준일**: `ServerTimeProvider.now()`의 날짜. QA 시각 조정을 따른다. 화면 "지금 기준" 줄에 보인다.
- **회차**: 그 주 월요일 00:00 ~ 다음 월요일 00:00. 번호는 활성 퀴즈셋이 있는 주를 오래된 순으로 센 값이라, 퀴즈셋을 지우거나 비활성으로 돌리거나 과거 주에 만들면 번호가 밀린다. 같은 주에 QA 용 비활성 셋이 있어도 매칭처럼 활성 셋만 본다.
- **연령대**: `birth_date`로 기준일의 만 나이를 구한다. `age` 컬럼은 가입 화면의 나이대 시작값(20·25·30 …)이라 쓰지 않는다. 20~23 / 24~26 / 27~29 / 30대.
- **가입 주차**: 고른 날이 속한 주(월~일)에 `joined_at`이 있는 회원.
- **더미**: dev 의 더미 회원(닉네임 `dummy-`)도 일반 회원처럼 센다. 운영은 QA 도구가 꺼져 있어 더미가 없다. 더미는 만들 때 고른 나이가 그날 만 나이가 되는 생년월일을 무작위로 받는다.

## 유저 현황

| 블록 | 세는 것 |
|---|---|
| 누적 가입자 | `joined_at` 있는 회원. 탈퇴(LEFT) 포함. 가입을 마치지 않은 PENDING 은 `joined_at`이 없어 빠진다 |
| 현재 회원 | 누적 가입자 중 LEFT 가 아닌 회원. 정지(SUSPENDED·BANNED) 포함 |
| 탈퇴 | 누적 가입자 중 LEFT |
| 성비·연령대 × 성별 | 현재 회원 |
| 회차 일별·고른 회차까지 8주 신규 | 그 기간 `joined_at`. 나중에 탈퇴한 회원도 가입한 날에 센다 |

매칭 유형(1:1·그룹) 조건은 이 장에 걸리지 않는다.

## 퀴즈 참여

| 블록 | 세는 것 |
|---|---|
| 회차 참여자 | 그 회차 활성 셋을 완주(`quiz_progress.status = COMPLETED`)한 회원 |
| 시작 | 그 회차 활성 셋에 진행 행이 있는 회원. 행은 첫 답을 낼 때 생기고, 진행을 초기화하면 지워져 빠진다 |
| 참여율 | 참여자 ÷ 활성 유저. 활성 유저는 고른 회차까지 최근 4회차 안에 한 번이라도 완주한 회원(겹치면 한 명) |
| 완료율 | 참여자 ÷ 시작 |
| 매칭 유형별 참여자·성비 | 참여자를 1:1·그룹으로 나눈 것 |

- 매칭 유형은 그 회차에 푼 퀴즈셋의 `matching_type`이다(한 주에 하나만 풀 수 있다). 기획서의 "트랙"이다. 조건은 참여자와 활성 유저 양쪽에 같이 걸린다.
- 그때 참여한 사실을 세므로 나중에 탈퇴한 회원도 들어간다. 분자와 분모가 같은 기준이라 참여율이 100%를 넘지 않는다.

## 손으로 대조하기

필터 없이 본 화면과 맞춰 보는 SQL 이다. 성별·연령대·가입 주차를 걸었으면 같은 조건을 `WHERE`에 더한다.

```sql
-- 누적 가입자·현재 회원·탈퇴
SELECT status, COUNT(*) FROM member
WHERE role = 'USER' AND joined_at IS NOT NULL
GROUP BY status;

-- 성비 (현재 회원)
SELECT gender, COUNT(*) FROM member
WHERE role = 'USER' AND joined_at IS NOT NULL AND status <> 'LEFT'
GROUP BY gender;

-- 연령대 (현재 회원, 기준일 :today 의 만 나이)
SELECT TIMESTAMPDIFF(YEAR, birth_date, :today) AS age, gender, COUNT(*) FROM member
WHERE role = 'USER' AND joined_at IS NOT NULL AND status <> 'LEFT'
GROUP BY age, gender;

-- 회차 참여자·시작 (매칭 유형별)
SELECT qs.matching_type, qp.status, COUNT(*) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
WHERE qs.week_started_on = :monday
GROUP BY qs.matching_type, qp.status;

-- 활성 유저 (:rounds 는 화면에 나온 최근 4회차 월요일들)
SELECT COUNT(DISTINCT qp.member_id) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
WHERE qs.week_started_on IN (:rounds) AND qp.status = 'COMPLETED';

-- 회차 일별 신규 (:monday 는 고른 회차의 월요일)
SELECT DATE(joined_at), COUNT(*) FROM member
WHERE role = 'USER' AND joined_at >= :monday AND joined_at < :monday + INTERVAL 7 DAY
GROUP BY DATE(joined_at);
```

## 알아 둘 것

- `joined_at`은 실제 시각으로 저장된다(`Member.register`). QA 시각 조정을 걸어도 새로 가입한 회원은 실제 날짜에 잡히므로, 회차 경계는 시각 조정만으로 재현되지 않는다.
- 탈퇴 30일 뒤 완전 삭제(`LeftMemberPurgeService`)를 켜면 그 회원은 누적·탈퇴·신규 숫자에서 모두 빠진다. 지금은 dry-run 이라 지워지지 않는다. 켜기 전에 회차 스냅샷을 정한다.
