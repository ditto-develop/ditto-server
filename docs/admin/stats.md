# 어드민 운영 지표 (`/admin/stats`)

기획 "디토 어드민 운영 지표 정의서"의 지표를 회차·회원 조건으로 본다. 구조(서버는 집계 결과만, 계산은 JS)는 [ADR 0046](../adr/0046-admin-stats-render-in-browser.md).

## 공통

- **관리자 제외**: `role = ADMIN` 은 모든 지표에서 뺀다. 채팅만 예외다.
- **기준일**: `ServerTimeProvider.now()`의 날짜. QA 시각 조정을 따른다. 화면 "오늘(…) 기준" 줄에 보인다.
- **회차**: 그 주 월요일 00:00 ~ 다음 월요일 00:00. 번호는 활성 퀴즈셋이 있는 주를 오래된 순으로 센 값이라, 퀴즈셋을 지우거나 비활성으로 돌리거나 과거 주에 만들면 번호가 밀린다. 같은 주에 QA 용 비활성 셋이 있어도 매칭처럼 활성 셋만 본다.
- **연령대**: `birth_date`로 기준일의 만 나이를 구한다. `age` 컬럼은 가입 화면의 나이대 시작값(20·25·30 …)이라 쓰지 않는다. 20~23 / 24~26 / 27~29 / 30대.
- **가입 주차**: 고른 날이 속한 주(월~일)에 `joined_at`이 있는 회원.
- **더미**: dev 의 더미 회원(닉네임 `dummy-`)도 일반 회원처럼 센다. 운영은 QA 도구가 꺼져 있어 더미가 없다. 더미는 만들 때 고른 나이가 그날(실제 날짜) 만 나이가 되는 생년월일을 무작위로 받는다. 시각 조정을 몇 주 앞으로 걸면 기준일이 달라 연령대가 고른 나이와 한 칸 어긋날 수 있다.
- **탈퇴·삭제**: 그때의 사실을 세므로 나중에 탈퇴한 회원도 들어간다. 탈퇴 30일 뒤 완전 삭제된 회원은 행이 없어 빠진다(지금은 삭제가 dry-run).
- **빈 상태**: 장마다 분모가 0이면 숫자 대신 안내를 띄운다. 매칭 유형이나 회원 조건을 걸었으면 "조건에 맞는 …"으로 알린다. 고른 주가 회차가 아니면 페이지 위에 한 번 알리고, 회차로 세는 장(퀴즈 참여·문항 분포·매칭·채팅·평가·리텐션)은 "회차가 아닌 주"로 비운다.

## 회원 현황

기획서의 "유저 현황"이다. 화면은 다른 어드민 화면처럼 "회원"으로 부른다.

| 블록 | 세는 것 |
|---|---|
| 누적 가입자 | PENDING 이 아닌 회원. 탈퇴(LEFT) 포함. 가입을 마치지 않은 PENDING 은 "가입 미완료"로 카드 아래에 따로 보인다 |
| 현재 회원 | 누적 가입자 중 LEFT 가 아닌 회원. 정지(SUSPENDED·BANNED) 포함 |
| 탈퇴 | 누적 가입자 중 LEFT |
| 계정 상태 | 현재 회원을 활성·휴면·정지(SUSPENDED)·영구 차단(BANNED)으로 나눈다. 활성은 ACTIVE 중 지금 회차까지 최근 4회차 안에 한 번이라도 완주한 회원, 휴면은 나머지 ACTIVE 다 |
| 지역 | 현재 회원의 `location`. 회원이 있는 지역만 많은 순으로 보이고 비었으면 "미입력" |
| 탈퇴 사유 | 고른 회차 주(월~일)에 `left_at`이 있는 회원을 `leave_reason`별로 센다. 자유 서술(`leave_reason_detail`)은 보여 주지 않는다 |
| 성비·연령대 × 성별 | 현재 회원 |
| 회차 일별·주별(고른 주까지 8주) 신규 | 그 기간 `joined_at`. 나중에 탈퇴한 회원도 가입한 날에 센다 |

매칭 유형(1:1·그룹) 조건은 이 장에 걸리지 않는다.

- 계정 상태는 `member.status`에 이력이 없어 지금 상태로만 센다. 그래서 활성도 고른 회차가 아니라 지금 회차 기준이다. 퀴즈 참여 장의 활성 회원은 고른 회차 기준이라 지난 회차를 보면 두 숫자가 다르다.
- 정지 회원은 최근에 완주했어도 정지로 센다.
- 탈퇴 사유 코드는 FE 탈퇴 화면(`withdrawReasons.ts`)이 정한 문자열이라 서버에 enum 이 없다. 라벨은 `stats-calc.js`에 그 화면 문구대로 두고, 모르는 코드는 코드 그대로 보인다. 사유 없이 탈퇴하면 "사유 없음"이다.
- 탈퇴 시각은 `ServerTimeProvider` 시각이라 QA 시각 조정을 따른다. 30일 안에 복구하면 `left_at`과 사유가 지워져 빠진다.

## 퀴즈 참여

| 블록 | 세는 것 |
|---|---|
| 회차 참여자 | 그 회차 활성 셋을 완주(`quiz_progress.status = COMPLETED`)한 회원 |
| 시작 | 그 회차 활성 셋에 진행 행이 있는 회원. 행은 첫 답을 낼 때 생기고, 진행을 초기화하면 지워져 빠진다 |
| 참여율 | 참여자 ÷ 활성 회원. 활성 회원은 고른 회차까지 최근 4회차 안에 한 번이라도 완주한 회원(겹치면 한 명) |
| 완료율 | 참여자 ÷ 시작 |
| 매칭 유형별 참여자·성비 | 참여자를 1:1·그룹으로 나눈 것 |
| 요일별 완주 | 참여자가 마지막 문항에 답한 요일(월~일). `quiz_progress`에 완주 시각이 없어 그 셋에 낸 답의 `created_at` 최댓값으로 본다 |

- 매칭 유형은 그 회차에 푼 퀴즈셋의 `matching_type`이다(한 주에 하나만 풀 수 있다). 기획서의 "트랙"이다. 조건은 참여자와 활성 회원 양쪽에 같이 걸린다.
- 그때 참여한 사실을 세므로 나중에 탈퇴한 회원도 들어간다. 분자와 분모가 같은 기준이라 참여율이 100%를 넘지 않는다.
- 답 시각은 JPA auditing 이 찍는 실제 시각이라 QA 시각 조정을 따르지 않는다. 답을 바꾸면 `updated_at`만 바뀌어 완주 요일은 그대로다.

## 문항 분포

- 그 회차 활성 퀴즈셋의 문항마다 선택지별 답 수와 비율. 매칭 유형을 고르면 그 유형 퀴즈셋만 나온다.
- 성별·연령대·가입 주차 조건은 답한 회원에게 건다. 답이 없는 선택지도 0으로 나온다.
- 완주하지 않은 회원의 답도 센다. 중간에 그만둔 회원은 뒤 문항에 답이 없어서, 문항마다 응답 수가 줄어드는 것으로 이탈 지점도 보인다.
- 쏠림 기준선은 기획서에 없어 화면에 표시하지 않는다. 비율만 보여 준다.

## 매칭

| 블록 | 세는 것 |
|---|---|
| 완주 | 퀴즈 참여 장의 회차 참여자와 같다 |
| 후보 | 1:1 은 `match_candidate`에 후보가 생긴 회원(`owner_member_id`). 그룹은 후보 그룹(`group_match_member`)에 들어간 회원이고 거절·미성사도 센다 |
| 성사 | 1:1 은 `personal_match.status = ACCEPTED`의 두 사람. 그룹은 성사된 그룹(`group_match.is_active`)을 수락(`ACCEPTED`)한 회원 |
| 매칭률 | 성사 ÷ 완주 |
| 미매칭 | 완주 − 성사. 그룹을 수락했지만 3명이 안 모인 회원도 미매칭이다. 성별 구성도 같은 뺄셈을 성별마다 한다 |

- 기획서의 매칭은 "짝을 정해 준다"지만 디토는 후보를 보여 주고 신청·수락으로 성사된다. 그래서 매칭 = 성사로 세고 후보는 참고로 보여 준다(#329). 1:1 후보는 자격 상대만 있으면 누구나 받아서(ADR 0040) 거의 100%다.
- 배치는 목요일 00:01 에 돈다. 그 전에는 후보·성사가 0 이다. 그룹은 금요일 00:00 마감까지 성사가 늘 수 있다.
- 서버는 테이블마다 센 값만 내리고 후보·성사로 나누는 건 `stats-calc.js`가 한다. 1:1 성사는 한 행에 회원이 둘이라 `member_id_1` 쪽과 `member_id_2` 쪽을 따로 세서 내린다.
- 매칭 유형 조건은 화면이 그 유형 줄만 그려서 건다. 회원 조건은 세는 회원에게 건다.
- 성사 뒤 진행을 초기화하면 완주에서 빠져 성사가 완주보다 클 수 있다. 미매칭은 0 에서 자른다.

## 채팅

| 블록 | 세는 것 |
|---|---|
| 열린 방 | 그 회차의 방 중 `SCHEDULED`가 아닌 방(열린 방·끝난 방) |
| 대화 시작률 | 대화 메시지(`TEXT`·`IMAGE`, `SYSTEM` 제외)가 하나라도 있는 방 ÷ 열린 방 |
| 양방향 대화율 | 2명 이상이 대화 메시지를 보낸 방 ÷ 열린 방 |
| 전원 (그룹만) | 참여자(`chat_room_member`, 나간 사람 포함) 전원이 대화 메시지를 보낸 방 ÷ 열린 방. 두 사람 방은 양방향과 같아 비운다 |

- 회차: 원본 매칭(1:1 `personal_match`, 그룹 `group_match`, 재매칭 `rematch`)의 퀴즈셋이 그 주 활성 셋인 방이다. 재매칭 방은 성사 뒤 처음 오는 금요일에 열리지만 원래 그룹의 퀴즈셋으로 센다. 그래서 매칭 장과 같은 회차에 잡히고 비활성 셋의 방은 빠진다.
- 관리자가 든 방도 센다. 방을 빼면 상대 회원의 대화까지 빠지고, 관리자만 빼면 1:1 방이 한 사람 방이 되기 때문이다(#329). 운영에서 팀원 계정이 관리자로 매칭에 참여하고 있어 다른 장과 숫자가 어긋날 수 있다.
- 재매칭은 따로 한 줄이고 1:1·그룹 합계에 넣지 않는다. 서로 원한 두 사람이라 수치가 높게 나온다. 매칭 유형 "전체"·"그룹"에서 보인다. 재매칭은 평가 제출로 뒤늦게 성사되어 지난 회차 숫자가 나중에 늘 수 있다.
- 방 단위 지표라 성별·연령대·가입 주차 조건은 걸지 않는다. 매칭 유형은 화면이 그 유형 줄만 골라 그린다.
- 다른 장과 달리 서버가 방마다 한 행(참여자 수·보낸 사람 수)을 내린다. 방별 두 수를 묶은 분포는 쿼리가 복잡해져서, 비율은 `stats-calc.js`가 방 행으로 계산한다. 방 수는 회차당 많아야 수백이다.

## 평가

| 블록 | 세는 것 |
|---|---|
| 평가지 | 그 회차(`member_review.week_started_on`) 매칭에서 열린 평가. 한 사람 × 한 채팅방이 한 건 |
| 제출률 | 완료(`COMPLETED`) ÷ 평가지. 대상 일부만 답한 진행 중(`IN_PROGRESS`)은 제출로 치지 않고 따로 보여 준다 |
| 만남 상태·별점 | 응답(확정된 답, `review_answer.answered_at` 있음). 그룹은 대상마다 한 건이라 평가지보다 많다 |

- 회원 조건은 평가를 쓴 사람에게 건다.
- 매칭 유형 1:1 은 평가의 `PERSONAL`, 그룹은 `GROUP` 이다(`MatchingTypeFilter`). 재매칭 채팅은 평가를 열지 않아 없다.
- 프로필의 받은 평가 집계와 달리 방이 끝났는지는 보지 않는다. 열린 그룹 방에서 나간 사람이 먼저 낸 평가도 센다.

## 리텐션

- 주간 재참여율 = 지난 회차 참여자 중 고른 회차에도 완주한 회원 ÷ 지난 회차 참여자.
- 지난 회차는 고른 회차 바로 앞의 회차(활성 퀴즈셋이 있는 주)다. 퀴즈를 쉰 주는 건너뛴다. 앞에 회차가 없거나 고른 주가 회차가 아니면 비운다.
- 매칭 유형은 지난 회차에 푼 유형이다. 고른 회차에는 어느 유형이든 완주하면 다시 참여한 것으로 센다(1:1 → 그룹으로 옮긴 회원도 포함).
- 트랙 전환은 다시 참여한 회원을 (지난 회차 유형 → 고른 회차 유형) 네 줄로 나눈다. 매칭 유형 조건은 지난 회차 쪽에만 걸려서, 하나를 고르면 그 유형에서 출발한 두 줄만 나온다.
- 회원 조건은 지난 회차 참여자에게 건다.

## 신고

- 고른 회차 주(월~일)에 접수(`member_report.created_at`)된 신고. 처리 상태는 지금 상태다.
- 접수 시각은 실제 시각이다. 시각 조정을 걸고 신고해도 실제 날짜의 주에 잡힌다.
- 평가일(월)에 낸 신고는 다음 회차 주에 잡힌다.
- 회원 조건은 피신고자에게 건다. 관리자가 피신고자인 신고는 뺀다.
- 신고에는 어느 매칭에서 나왔는지가 없다. 매칭 유형을 고르면 피신고자가 그 회차에 그 유형 퀴즈셋을 완주한 신고만 센다. 한 주에 한 유형만 풀 수 있어 근사가 크게 어긋나지 않지만, 프로필 화면에서 들어온 신고처럼 매칭과 상관없는 신고도 섞인다. "전체"는 퀴즈에 참여하지 않은 피신고자의 신고도 센다.
- 사유는 여러 개 고를 수 있어 사유별 수를 더하면 신고 건수보다 클 수 있다.

## 차단

- 고른 회차 주(월~일)에 생긴(`member_block.created_at`) 차단 수. 차단 시각은 실제 시각이다.
- 차단을 풀면 행이 지워진다(`MemberBlockService.unblock`). 그래서 지금 유지 중인 차단만 센다. 해제를 소프트 삭제로 바꾸지 않기로 했다(#329). 해제가 잦아지면 다시 본다.
- 신고하면서 함께 건 차단(`UserReportService`)도 같은 테이블이라 센다.
- 회원 조건과 매칭 유형은 신고와 같이 차단된 사람에게 건다. 관리자가 차단된 건은 뺀다.

## 알림

| 블록 | 세는 것 |
|---|---|
| 알림 건수 | 고른 회차 주(월~일, 실제 시각)의 푸시 발송 이력(`push_send_history`) 건수. 알림 한 건이 한 행이다 |
| 보냄·건너뜀 | 건너뛴 이유(`skip_reason`)가 비었으면 보낸 것, 있으면 건너뛴 것(채팅방 알림 꺼짐·알림 설정 꺼짐·등록된 기기 없음) |
| 실패율 | 보낸 알림 중 실패 기기가 하나라도 있는 알림 ÷ 보낸 알림 |
| 푸시 토큰 등록률 | 퀴즈 참여 장의 활성 회원 중 지금 푸시 토큰(`member_device`)이 있는 회원 ÷ 활성 회원. 이력이 없는 주에도 보인다 |

- 이력은 기록을 시작한 뒤(#339 배포)부터 쌓여서 그 전 회차는 비어 있다. 180일 뒤 지워진다.
- 실패는 FCM 이 받지 않은 것이다. 폰에 실제로 떴는지는 모른다. iOS 알림 권한을 끈 기기도 보낸 것으로 센다.
- 새 메시지 알림은 알림 센터에서 안 읽은 이전 알림을 접어 한 줄로 보이지만, 이력은 보낼 때마다 한 행이라 메시지 수만큼 생긴다.
- 발송 건수는 매칭 유형과 관계가 없어 매칭 유형으로 거르지 않는다. 회원 조건은 받는 회원에게 건다. 관리자에게 간 알림은 뺀다.
- 토큰 등록률의 분자는 지금 토큰 기준이지만 분모는 퀴즈 참여 장의 활성 회원이라 고른 회차에 따라 바뀌고 매칭 유형 조건도 같이 걸린다. 활성 회원이 없으면 "활성 회원 없음"이 보인다.
- 유형별 표는 보낸 알림이 많은 순이다. 실패 코드 `SEND_ERROR`는 FCM 에 넘기기 전에 서버에서 실패한 것이다.
- 알림 유형 라벨은 어드민 화면에서만 쓰여서 서버 enum 이 아니라 `stats-calc.js`에 둔다. 새 유형을 더하면 거기도 더한다. 빠지면 enum 이름 그대로 보인다.

## 손으로 대조하기

필터 없이 본 화면과 맞춰 보는 SQL 이다. 성별·연령대·가입 주차를 걸었으면 같은 조건을 `WHERE`에 더한다.

```sql
-- 누적 가입자·현재 회원·탈퇴·계정 상태 (PENDING 은 가입 미완료)
SELECT status, COUNT(*) FROM member
WHERE role = 'USER'
GROUP BY status;

-- 계정 상태의 활성 (:recent_rounds 는 지금 회차까지 최근 4회차의 월요일들)
SELECT m.status, COUNT(DISTINCT qp.member_id) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
WHERE qs.week_started_on IN (:recent_rounds) AND qp.status = 'COMPLETED'
GROUP BY m.status;

-- 지역 (현재 회원)
SELECT location, COUNT(*) FROM member
WHERE role = 'USER' AND joined_at IS NOT NULL AND status <> 'LEFT'
GROUP BY location;

-- 탈퇴 사유 (고른 회차 주)
SELECT leave_reason, COUNT(*) FROM member
WHERE role = 'USER' AND left_at >= :monday AND left_at < :monday + INTERVAL 7 DAY
GROUP BY leave_reason;

-- 성비 (현재 회원)
SELECT gender, COUNT(*) FROM member
WHERE role = 'USER' AND joined_at IS NOT NULL AND status <> 'LEFT'
GROUP BY gender;

-- 연령대 (현재 회원, 기준일 :today 의 만 나이)
SELECT TIMESTAMPDIFF(YEAR, birth_date, :today) AS age, gender, COUNT(*) FROM member
WHERE role = 'USER' AND joined_at IS NOT NULL AND status <> 'LEFT'
GROUP BY age, gender;

-- 회차 참여자·시작 (매칭 유형별). 참여자 = COMPLETED, 시작 = 상태와 상관없이 행 수(행은 첫 답을 낼 때 생긴다)
SELECT qs.matching_type, qp.status, COUNT(*) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
WHERE qs.week_started_on = :monday
GROUP BY qs.matching_type, qp.status;

-- 요일별 완주 (1 = 일요일). 회원마다 그 셋에 낸 마지막 답 시각
SELECT DAYOFWEEK(completed_at) AS day_of_week, COUNT(*) FROM (
  SELECT MAX(qa.created_at) AS completed_at FROM quiz_answer qa
  JOIN quiz q ON q.id = qa.quiz_id
  JOIN quiz_set qs ON qs.id = q.quiz_set_id AND qs.is_active AND qs.week_started_on = :monday
  JOIN quiz_progress qp ON qp.member_id = qa.member_id AND qp.quiz_set_id = qs.id AND qp.status = 'COMPLETED'
  JOIN member m ON m.id = qa.member_id AND m.role = 'USER'
  GROUP BY qa.member_id
) completed
GROUP BY day_of_week;

-- 문항 분포
SELECT q.display_order, q.question, c.content, COUNT(m.id) FROM quiz_choice c
JOIN quiz q ON q.id = c.quiz_id
JOIN quiz_set qs ON qs.id = q.quiz_set_id AND qs.is_active
LEFT JOIN quiz_answer a ON a.choice_id = c.id
LEFT JOIN member m ON m.id = a.member_id AND m.role = 'USER'
WHERE qs.week_started_on = :monday
GROUP BY qs.id, q.id, c.id
ORDER BY qs.id, q.display_order, c.display_order;

-- 활성 회원 (:rounds 는 화면 퀴즈 참여 장 기준 줄에 나온 회차 월요일들)
SELECT COUNT(DISTINCT qp.member_id) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
WHERE qs.week_started_on IN (:rounds) AND qp.status = 'COMPLETED';

-- 1:1 후보
SELECT m.gender, COUNT(DISTINCT c.owner_member_id) FROM match_candidate c
JOIN quiz_set qs ON qs.id = c.quiz_set_id AND qs.is_active
JOIN member m ON m.id = c.owner_member_id AND m.role = 'USER'
WHERE qs.week_started_on = :monday
GROUP BY m.gender;

-- 1:1 성사 (두 사람을 각각)
SELECT m.gender, COUNT(*) FROM personal_match pm
JOIN quiz_set qs ON qs.id = pm.quiz_set_id AND qs.is_active
JOIN member m ON m.id IN (pm.member_id_1, pm.member_id_2) AND m.role = 'USER'
WHERE qs.week_started_on = :monday AND pm.status = 'ACCEPTED'
GROUP BY m.gender;

-- 그룹 후보 = 전부, 성사 = is_active 이고 ACCEPTED
SELECT gm.is_active, gmm.status, m.gender, COUNT(*) FROM group_match_member gmm
JOIN group_match gm ON gm.id = gmm.room_id
JOIN quiz_set qs ON qs.id = gm.quiz_set_id AND qs.is_active
JOIN member m ON m.id = gmm.member_id AND m.role = 'USER'
WHERE qs.week_started_on = :monday
GROUP BY gm.is_active, gmm.status, m.gender;

-- 채팅 유형별 열린 방·대화 시작·양방향·전원 (:monday 회차, 원본 매칭의 활성 퀴즈셋)
SELECT source_type, COUNT(*) AS opened,
    SUM(senders >= 1) AS started, SUM(senders >= 2) AS two_way, SUM(senders >= members) AS all_sent
FROM (
    SELECT r.source_type,
        (SELECT COUNT(*) FROM chat_room_member crm WHERE crm.room_id = r.id) AS members,
        (SELECT COUNT(DISTINCT msg.sender_id) FROM chat_message msg
         WHERE msg.room_id = r.id AND msg.message_type <> 'SYSTEM') AS senders
    FROM chat_room r
    LEFT JOIN personal_match pm ON r.source_type = 'PERSONAL' AND pm.id = r.source_id
    LEFT JOIN group_match gm ON r.source_type = 'GROUP' AND gm.id = r.source_id
    LEFT JOIN rematch rm ON r.source_type = 'REMATCH' AND rm.id = r.source_id
    JOIN quiz_set qs ON qs.id = COALESCE(pm.quiz_set_id, gm.quiz_set_id, rm.quiz_set_id) AND qs.is_active
    WHERE r.status <> 'SCHEDULED' AND qs.week_started_on = :monday
) rooms
GROUP BY source_type;

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

-- 주간 재참여·트랙 전환 (:previous 는 지난 회차 월요일). 분모는 위 참여자 SQL 을 :previous 로 돌린 COMPLETED 수
SELECT qs.matching_type, COUNT(DISTINCT qp.member_id) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active AND qs.week_started_on = :previous
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
JOIN quiz_progress again ON again.member_id = qp.member_id AND again.status = 'COMPLETED'
JOIN quiz_set again_set ON again_set.id = again.quiz_set_id AND again_set.is_active AND again_set.week_started_on = :monday
WHERE qp.status = 'COMPLETED'
GROUP BY qs.matching_type, again_set.matching_type;

-- 신고 처리 상태 (피신고자 기준)
SELECT r.status, COUNT(*) FROM member_report r
JOIN member m ON m.id = r.reported_member_id AND m.role = 'USER'
WHERE r.created_at >= :monday AND r.created_at < :monday + INTERVAL 7 DAY
GROUP BY r.status;

-- 신고 사유별 (reasons 는 콤마로 이은 문자열이라 사유마다 포함 여부를 센다)
SELECT
    SUM(FIND_IN_SET('INAPPROPRIATE_BEHAVIOR', r.reasons) > 0) AS inappropriate_behavior,
    SUM(FIND_IN_SET('MONEY_DEMAND', r.reasons) > 0) AS money_demand,
    SUM(FIND_IN_SET('FALSE_INFORMATION', r.reasons) > 0) AS false_information,
    SUM(FIND_IN_SET('UNDERAGE', r.reasons) > 0) AS underage,
    SUM(FIND_IN_SET('ETC', r.reasons) > 0) AS etc
FROM member_report r
JOIN member m ON m.id = r.reported_member_id AND m.role = 'USER'
WHERE r.created_at >= :monday AND r.created_at < :monday + INTERVAL 7 DAY;

-- 알림 발송 이력 (받는 회원 기준). skip_reason 이 NULL 이면 보낸 것, failed_device_count > 0 이면 실패
SELECT h.notification_type, h.skip_reason, h.failed_device_count > 0 AS failed, COUNT(*),
    GROUP_CONCAT(DISTINCT h.failure_codes) AS failure_codes
FROM push_send_history h
JOIN member m ON m.id = h.member_id AND m.role = 'USER'
WHERE h.created_at >= :monday AND h.created_at < :monday + INTERVAL 7 DAY
GROUP BY h.notification_type, h.skip_reason, failed;

-- 푸시 토큰 등록률 분자 (:rounds 는 활성 회원 SQL 과 같은 회차들)
SELECT COUNT(DISTINCT qp.member_id) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
WHERE qs.week_started_on IN (:rounds) AND qp.status = 'COMPLETED'
  AND EXISTS (SELECT 1 FROM member_device d WHERE d.member_id = qp.member_id);

-- 미수신 문의: 한 회원의 최근 발송 이력. 행이 없으면 알림이 쌓이지 않았거나(중복 정책·탈퇴),
-- 발송 준비나 이력 저장이 실패한 것이다(뒤의 둘은 서버 WARN 로그로만 남는다).
SELECT created_at, notification_type, skip_reason, device_count, failed_device_count, failure_codes
FROM push_send_history WHERE member_id = :memberId ORDER BY created_at DESC LIMIT 50;

-- 그 회원의 지금 기기와 알림 설정 (설정 행이 없으면 기본값: 매칭·채팅 켜짐)
SELECT platform, created_at FROM member_device WHERE member_id = :memberId;
SELECT * FROM member_notification_setting WHERE member_id = :memberId;

-- 차단 수 (차단된 사람 기준)
SELECT COUNT(*) FROM member_block b
JOIN member m ON m.id = b.blocked_member_id AND m.role = 'USER'
WHERE b.created_at >= :monday AND b.created_at < :monday + INTERVAL 7 DAY;

-- 회차 일별 신규 (:monday 는 고른 회차의 월요일)
SELECT DATE(joined_at), COUNT(*) FROM member
WHERE role = 'USER' AND joined_at >= :monday AND joined_at < :monday + INTERVAL 7 DAY
GROUP BY DATE(joined_at);
```

## dev 에서 재현하기

테스트 계정은 `USER` 계정을 쓴다. 어드민 로그인 계정(`ADMIN`)으로 앱을 쓰면 채팅을 뺀 모든 장에서 빠진다.

| 장 | 쓸 도구 | 주의 |
|---|---|---|
| 회원 현황 | 더미 생성(지역은 무작위, 직접 만들기에서 고를 수 있다). 정지는 신고 검토에서 제재, 탈퇴 사유는 테스트 계정으로 앱에서 탈퇴 | 가입 시각은 실제 시각이라 일별 표에는 실제 오늘 줄에만 늘어난다. 시각 조정 중이면 실제 날짜의 주를 `?week=yyyy-MM-dd`로 연다(드롭다운에는 조정된 이번 주와 회차만 나온다). 탈퇴 시각은 조정된 시각이라 조정된 주에 잡힌다. 가입 미완료는 소셜 로그인만 하고 가입 화면을 닫으면 생긴다 |
| 퀴즈 참여 | 더미 생성(일괄은 늘 완주), 더미 직접 만들기의 "푼 문항 수" | 푼 문항 수 0 이면 진행 행이 없어 "시작"에도 안 잡힌다. 비활성 퀴즈셋에 만든 더미는 세지 않는다(더미 화면에 표시됨) |
| 문항 분포 | 더미 직접 만들기의 문항별 선택지 지정 | 일부만 푼 더미는 앞 문항에만 답이 있다 |
| 매칭 | 시각 조정을 그 회차 주(월~수)에 걸고 테스트 계정이 앱에서 퀴즈를 푼다 → 더미 생성 → "퀴즈 마감 직후"로 옮겨 매칭 실행(더미를 나중에 더했으면 퀴즈셋별 재생성) → QA 콘솔에서 1:1 신청·수락, 그룹 수락 | 1:1 은 상대가 실회원인 쌍만 콘솔이 다뤄서 테스트 계정이 같은 1:1 셋을 풀어 둬야 한다. 콘솔은 서버 시각 주의 퀴즈셋만 보여 준다. 그룹은 금요일 00:00 마감 전에 수락해야 하고 응답이 시작되면 재생성할 수 없다. 목요일 매칭 전에는 후보·성사가 0 이다 |
| 채팅 | 매칭 성사 → QA 콘솔 시각 조정 "채팅방 열린 직후" → 앱이나 QA 콘솔로 메시지 보내기 | 열리기 전(`SCHEDULED`) 방은 세지 않는다. 회차는 원본 매칭의 퀴즈셋이라 매칭 장과 같은 회차에 잡힌다. 재매칭 방은 성사 뒤 다음 금요일에 열린다. 재매칭 방 화면의 [옮기기]나 "채팅방 열린 직후"로 연 뒤 원래 그룹 회차를 연다 |
| 평가 | 매칭 실행 → QA 콘솔 시각 조정 "채팅방 열린 직후" → QA 콘솔에서 1:1 은 "채팅 종료", 그룹은 더미를 "나가기" → 더미로 평가 제출 | 그룹방은 채팅 종료가 없다. 나간 더미의 평가지만 바로 열리고, 1명만 남으면 방이 해체되어 전원 평가지가 열린다. 그룹방에서 상대 일부만 평가하면 "진행 중"이 된다 |
| 리텐션 | 테스트 계정으로 활성 퀴즈셋이 있는 연이은 두 회차에 시각 조정(각 월~수)을 걸고 앱에서 직접 풀기 | 사이에 다른 활성 셋 주가 끼면 그 주가 지난 회차가 된다. 미래 주를 쓰면 그 주 퀴즈셋을 먼저 만든다. 더미는 늘 새 회원이라 두 회차를 이어 풀 수 없다(#333) |
| 차단 | QA 콘솔 신고에서 "더미가 이 회원도 사용자 차단"을 체크해 신고, 또는 앱에서 테스트 계정으로 차단 | 콘솔로 하면 신고 장도 같이 는다. 같은 상대에게 검토 대기 신고가 있으면 다시 신고할 수 없어 다른 더미로 한다. 이미 차단한 쌍이면 차단 수는 그대로다. 차단 시각이 실제 시각이라 시각 조정 중인 회차에는 안 잡힌다. 실제 날짜의 주를 `?week=`로 연다. 매칭 유형 조건까지 보려면 실제 날짜 주의 활성 셋으로 더미를 만든다 |
| 알림 | 테스트 계정(USER, 앱에 로그인해 푸시 토큰 등록)과 더미에게 알림이 가는 동작을 한다. 예: 매칭 실행, QA 콘솔에서 테스트 계정에게 1:1 신청, QA 콘솔 방에서 더미로 메시지 보내기, 공지 발송(`/admin/notices`, 활성 회원 전원에게 감). 퀴즈 오픈 알림은 실제 시각 월 00:00 배치에서만 나가서 재현되지 않는다 | 더미는 푸시 토큰이 없어 "등록된 기기 없음"으로 남는다. 방 알림 꺼짐: 테스트 계정이 앱에서 그 방 알림을 끄고 더미로 메시지를 보낸다(채팅 알림만 해당). 알림 설정 꺼짐: 앱 설정에서 채팅·매칭 알림을 끄고 더미 메시지나 1:1 신청을 받는다. 공지는 설정과 상관없이 늘 보내서 이 경우를 못 만든다. 방 알림을 먼저 보므로 둘 다 꺼져 있으면 방 알림 꺼짐으로 남는다. 실패: 테스트 계정으로 아무 문자열을 푸시 토큰으로 등록(`POST /api/v1/notifications/devices`)하면 FCM 이 거절해 실패로 남는다. 이 토큰은 자동으로 지워지지 않으니 끝나면 `DELETE /api/v1/notifications/devices/{token}`으로 지운다. 안 지우면 그 계정 알림이 계속 실패로 잡힌다. 로컬(`local` 프로필)은 가짜 발송기라 늘 성공으로 남아 실패를 재현할 수 없다. 이력 시각은 실제 시각이라 시각 조정 중인 회차에는 안 잡힌다. 실제 날짜의 주를 `?week=`로 연다 |
| 신고 | QA 콘솔에서 더미로 신고 | 접수 시각이 실제 시각이라 시각 조정 중인 회차에는 안 잡힌다. 실제 날짜의 주를 `?week=`로 연다 |

더미 정리를 하면 더미의 진행·답·평가·신고와 함께 더미가 낀 방의 테스트 계정 평가, 테스트 계정이 더미를 신고한 건도 지워져 지난 회차 숫자가 바뀐다. 매칭·채팅·차단도 줄어든다. 더미가 낀 후보·1:1 매칭, 더미가 초대된 그룹(테스트 계정 초대까지), 그 매칭에서 나온 방, 더미가 하거나 받은 차단이 지워진다. 알림은 더미가 받은 발송 이력만 지워진다. 더미 때문에 테스트 계정이 받은 이력(더미의 메시지, 1:1 신청 등)은 테스트 계정 것이라 남는다. 푸시 토큰 등록률은 분모에서 더미 완주가 빠져 값이 오른다.

## 알아 둘 것

- `joined_at`은 실제 시각으로 저장된다(`Member.register`). QA 시각 조정을 걸어도 새로 가입한 회원은 실제 날짜에 잡히므로, 회차 경계는 시각 조정만으로 재현되지 않는다.
- 탈퇴 30일 뒤 완전 삭제(`LeftMemberPurgeService`)를 켜면 그 회원은 누적·탈퇴·신규 숫자에서 모두 빠진다. 지금은 dry-run 이라 지워지지 않는다. 켜기 전에 회차 스냅샷을 정한다.
