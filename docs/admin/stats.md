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

## 문항 분포

- 그 회차 활성 퀴즈셋의 문항마다 선택지별 답 수와 비율. 매칭 유형을 고르면 그 유형 퀴즈셋만 나온다.
- 성별·연령대·가입 주차 조건은 답한 회원에게 건다. 답이 없는 선택지도 0으로 나온다.
- 완주하지 않은 회원의 답도 센다. 중간에 그만둔 회원은 뒤 문항에 답이 없어서, 문항마다 응답 수가 줄어드는 것으로 이탈 지점도 보인다.
- 쏠림 기준선은 기획서에 없어 화면에 표시하지 않는다. 비율만 보여 준다.

## 평가

| 블록 | 세는 것 |
|---|---|
| 평가지 | 그 회차(`member_review.week_started_on`) 매칭에서 열린 평가. 한 사람 × 한 채팅방이 한 건 |
| 제출률 | 완료(`COMPLETED`) ÷ 평가지. 대상 일부만 답한 진행 중(`IN_PROGRESS`)은 제출로 치지 않고 따로 보여 준다 |
| 만남 상태·별점 | 확정된 답(`review_answer.answered_at` 있음). 그룹은 대상마다 한 건이라 평가지보다 많다 |

- 회원 조건은 평가를 쓴 사람에게 건다.
- 매칭 유형 1:1 은 평가의 `PERSONAL`, 그룹은 `GROUP` 이다(`MatchingTypeFilter`). 재매칭 채팅은 평가를 열지 않아 없다.
- 프로필의 받은 평가 집계와 달리 방이 끝났는지는 보지 않는다. 열린 그룹 방에서 나간 사람이 먼저 낸 평가도 센다.

## 리텐션

- 주간 재참여율 = 지난 회차 참여자 중 고른 회차에도 완주한 회원 ÷ 지난 회차 참여자.
- 지난 회차는 고른 회차 바로 앞의 회차(활성 퀴즈셋이 있는 주)다. 퀴즈를 쉰 주는 건너뛴다. 앞에 회차가 없으면 비운다.
- 매칭 유형은 지난 회차에 푼 유형이다. 고른 회차에는 어느 유형이든 완주하면 다시 참여한 것으로 센다(1:1 → 그룹으로 옮긴 회원도 포함). 유형 전환 수는 P1 이라 따로 보여 주지 않는다.
- 회원 조건은 지난 회차 참여자에게 건다.

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

-- 문항 분포
SELECT q.display_order, q.question, c.content, COUNT(m.id) FROM quiz_choice c
JOIN quiz q ON q.id = c.quiz_id
JOIN quiz_set qs ON qs.id = q.quiz_set_id AND qs.is_active
LEFT JOIN quiz_answer a ON a.choice_id = c.id
LEFT JOIN member m ON m.id = a.member_id AND m.role = 'USER'
WHERE qs.week_started_on = :monday
GROUP BY qs.id, q.id, c.id
ORDER BY qs.id, q.display_order, c.display_order;

-- 활성 유저 (:rounds 는 화면에 나온 최근 4회차 월요일들)
SELECT COUNT(DISTINCT qp.member_id) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
WHERE qs.week_started_on IN (:rounds) AND qp.status = 'COMPLETED';

-- 평가지 상태 (쓴 사람 기준)
SELECT r.match_type, r.status, COUNT(*) FROM member_review r
JOIN member m ON m.id = r.author_member_id AND m.role = 'USER'
WHERE r.week_started_on = :monday
GROUP BY r.match_type, r.status;

-- 만남 상태·별점 (확정된 답)
SELECT a.meeting_status, a.rating, COUNT(*) FROM review_answer a
JOIN member_review r ON r.id = a.member_review_id
JOIN member m ON m.id = r.author_member_id AND m.role = 'USER'
WHERE r.week_started_on = :monday AND a.answered_at IS NOT NULL
GROUP BY a.meeting_status, a.rating;

-- 주간 재참여 (:previous 는 지난 회차 월요일)
SELECT qs.matching_type, COUNT(DISTINCT qp.member_id) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active AND qs.week_started_on = :previous
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
JOIN quiz_progress again ON again.member_id = qp.member_id AND again.status = 'COMPLETED'
JOIN quiz_set again_set ON again_set.id = again.quiz_set_id AND again_set.is_active AND again_set.week_started_on = :monday
WHERE qp.status = 'COMPLETED'
GROUP BY qs.matching_type;

-- 회차 일별 신규 (:monday 는 고른 회차의 월요일)
SELECT DATE(joined_at), COUNT(*) FROM member
WHERE role = 'USER' AND joined_at >= :monday AND joined_at < :monday + INTERVAL 7 DAY
GROUP BY DATE(joined_at);
```

## 알아 둘 것

- `joined_at`은 실제 시각으로 저장된다(`Member.register`). QA 시각 조정을 걸어도 새로 가입한 회원은 실제 날짜에 잡히므로, 회차 경계는 시각 조정만으로 재현되지 않는다.
- 탈퇴 30일 뒤 완전 삭제(`LeftMemberPurgeService`)를 켜면 그 회원은 누적·탈퇴·신규 숫자에서 모두 빠진다. 지금은 dry-run 이라 지워지지 않는다. 켜기 전에 회차 스냅샷을 정한다.
