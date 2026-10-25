# 어드민 운영 지표 (`/admin/stats`)

기획 "디토 어드민 운영 지표 정의서"의 지표를 회차·회원 조건으로 본다. 구조(서버는 집계 결과만, 계산은 JS)는 [ADR 0046](../adr/0046-admin-stats-render-in-browser.md).

## 공통

- **관리자 계정**: `role = ADMIN` 은 기본으로 모든 지표에서 뺀다. 필터의 "관리자 계정 · 지표에 포함"(`includeAdmins=true`)을 켜면 일반 회원처럼 센다(#352). 채팅과 쌍·그룹 단위 지표는 켜든 끄든 관리자가 낀 것을 센다. 팀원이 관리자 계정으로 실제 퀴즈·매칭에 참여하면 숫자가 작게 보여서, 페이지 위에 탈퇴하지 않은 관리자 계정 수와 고른 회차에 퀴즈를 완주한 관리자 수를 알린다(#343). 이 둘은 회원 조건·매칭 유형을 걸지 않는다.
- **기준일**: `ServerTimeProvider.now()`의 날짜. QA 시간 조정을 따른다. 화면 "오늘(…) 기준" 줄에 보인다.
- **회차**: 그 주 월요일 00:00 ~ 다음 월요일 00:00. 번호는 활성 퀴즈셋이 있는 주를 오래된 순으로 센 값이라, 퀴즈셋을 지우거나 비활성으로 돌리거나 과거 주에 만들면 번호가 밀린다. 같은 주에 QA 용 비활성 셋이 있어도 매칭처럼 활성 셋만 본다.
- **연령대**: `birth_date`로 기준일의 만 나이를 구한다. `age` 컬럼은 가입 화면의 나이대 시작값(20·25·30 …)이라 쓰지 않는다. 20~23 / 24~26 / 27~29 / 30대.
- **가입 주차**: 고른 날이 속한 주(월~일)에 `joined_at`이 있는 회원.
- **더미**: dev 의 더미 회원(닉네임 `dummy-`)도 일반 회원처럼 센다. 운영은 QA 도구가 꺼져 있어 더미가 없다. 더미는 만들 때 고른 나이가 그날(실제 날짜) 만 나이가 되는 생년월일을 무작위로 받는다. 시간 조정을 몇 주 앞으로 걸면 기준일이 달라 연령대가 고른 나이와 한 칸 어긋날 수 있다.
- **탈퇴·삭제**: 그때의 사실을 세므로 나중에 탈퇴한 회원도 들어간다. 탈퇴 30일 뒤 완전 삭제된 회원은 행이 없어 빠진다(지금은 삭제가 dry-run).
- **기간 배지**: 기준 줄 앞에 그 숫자가 세는 기간을 붙인다. `이 회차`(고른 회차), `이 주 월~일`(고른 주의 실제 월~일, 회차가 아닌 주도 값이 있다), `최근 N회차`·`고른 주까지 N주`·`지난 회차 → 이 회차`(고른 회차가 끝점인 범위), `회차 무관 · 오늘 기준`(회차를 바꿔도 그대로). 필터가 걸리지 않는 장에는 "매칭 유형 미적용"·"회원 조건 미적용" 칩을 띄운다. 용어 설명은 장 제목과 카드 옆 (i)에 둔다.
- **장 순서**: 회원 → 퀴즈 참여 → 매칭 → 채팅 → 평가 → 리텐션 → 문항 분포 → 신고·사용자 차단 → 알림. 문항 분포는 길어서 흐름 뒤에 둔다.
- **빈 상태**: 장마다 분모가 0이면 숫자 대신 안내를 띄운다. 기준 줄은 빈 장에도 남겨 어느 기간을 조회했는지 보이게 한다. 매칭 유형이나 회원 조건을 걸었으면 "조건에 맞는 …"으로 알린다. 고른 주가 회차가 아니면 페이지 위에 한 번 알리고, 회차로 세는 장(퀴즈 참여·문항 분포·매칭·채팅·평가·리텐션)은 "회차가 아닌 주"로 비운다.

## 회원 현황

기획서의 "유저 현황"이다. 화면은 다른 어드민 화면처럼 "회원"으로 부른다.

| 블록 | 세는 것 |
|---|---|
| 누적 가입자 | `joined_at` 있는 회원. 탈퇴(LEFT) 포함. 가입을 마치지 않은 PENDING 은 "가입 미완료 N명 제외"로 카드 아래에 따로 보인다(0명이면 숨김) |
| 현재 회원 | 누적 가입자 중 LEFT 가 아닌 회원. 정지(SUSPENDED)·영구 차단(BANNED) 포함 |
| 현재 탈퇴 상태 | 누적 가입자 중 지금 LEFT. 30일 안에 재가입해 복구하면 ACTIVE 로 돌아가 빠지므로 탈퇴 누적 횟수가 아니다 |
| 계정 상태 | 현재 회원을 활성·휴면·정지(SUSPENDED)·영구 차단(BANNED)으로 나눈다. 활성은 ACTIVE 중 지금 회차까지 최근 4회차 안에 한 번이라도 완주한 회원, 휴면은 나머지 ACTIVE 다. 쓴 회차는 카드 아래에 보인다 |
| 지역 | 현재 회원의 `location`. 회원이 있는 지역만 많은 순으로 보이고 비었으면 "미입력" |
| 탈퇴 사유 | 고른 회차 주(월~일)에 `left_at`이 있는 가입자를 `leave_reason`별로 센다. 자유 서술(`leave_reason_detail`)은 보여 주지 않는다 |
| 성비·연령대 × 성별 | 현재 회원 |
| 일별(고른 주 월~일)·주별(고른 주까지 8주) 신규 | 그 기간 `joined_at`. 나중에 탈퇴한 회원도 가입한 날에 센다 |

매칭 유형(1:1·그룹) 조건은 이 장에 걸리지 않는다.

- 계정 상태는 `member.status`에 이력이 없어 지금 상태로만 센다. 그래서 활성도 고른 회차가 아니라 지금 회차 기준이다.
- 퀴즈 참여 장의 활성 회원과는 이번 회차에서도 숫자가 다를 수 있다. 퀴즈 장은 고른 회차 기준이고, 정지·탈퇴 회원도 완주했으면 세고, 매칭 유형 조건이 걸린다. 계정 상태 활성은 지금 ACTIVE 인 회원만 센다.
- 정지 회원은 최근에 완주했어도 정지로 센다.
- 탈퇴 사유는 enum 이 없는 문자열이다. FE 탈퇴 화면(`withdrawReasons.ts`)이 보내는 다섯 코드와, 애플 서버 알림으로 서버가 탈퇴시킬 때 넣는 `APPLE_ACCOUNT_DELETED`·`APPLE_CONSENT_REVOKED`가 있다. 라벨은 `stats-calc.js`에 두고, 모르는 코드는 코드 그대로 보인다. 사유 없이 탈퇴하면 "사유 없음"이다.
- 가입을 마치지 않은 회원도 애플 연결 해제로 탈퇴될 수 있다(#342). 이 회원은 `joined_at`이 없어 누적 가입자·탈퇴·탈퇴 사유에서 모두 빠진다.
- 탈퇴 시각은 `ServerTimeProvider` 시각이라 QA 시간 조정을 따른다. 30일 안에 복구하면 `left_at`과 사유가 지워져 빠진다.

## 퀴즈 참여

| 블록 | 세는 것 |
|---|---|
| 회차 참여자 | 그 회차 활성 셋을 완주(`quiz_progress.status = COMPLETED`)한 회원 |
| 시작 | 그 회차 활성 셋에 진행 행이 있는 회원. 행은 첫 답을 낼 때 생기고, 진행을 초기화하면 지워져 빠진다 |
| 참여율 | 참여자 ÷ 활성 회원. 활성 회원은 고른 회차까지 최근 4회차 안에 한 번이라도 완주한 회원(겹치면 한 명) |
| 완료율 | 참여자 ÷ 시작 |
| 매칭 유형별 참여자·성비 | 참여자를 1:1·그룹으로 나눈 것 |
| 요일별 완주 | 참여자가 마지막으로 답한 요일. `quiz_progress`에 완주 시각이 없어 그 셋에 낸 답의 `created_at` 최댓값으로 본다. 응답 기간인 월~수는 늘 보이고 다른 요일은 완주가 있을 때만 붙는다. 요일 합은 회차 참여자와 같다 |

- 매칭 유형은 그 회차에 푼 퀴즈셋의 `matching_type`이다(한 주에 하나만 풀 수 있다). 기획서의 "트랙"이다. 조건은 참여자와 활성 회원 양쪽에 같이 걸린다. 한 회원이 같은 주에 두 셋을 푸는 것은 앱이 조회 때 오류로 다루는 불변식 위반이라 지표는 이 전제를 따른다.
- 그때 참여한 사실을 세므로 나중에 탈퇴한 회원도 들어간다. 분자와 분모가 같은 기준이라 참여율이 100%를 넘지 않는다.
- 답 시각은 JPA auditing 이 찍는 실제 시각이라 QA 시간 조정을 따르지 않는다. 답을 바꾸면 `updated_at`만 바뀌어 완주 요일은 그대로다.

## 문항 분포

- 그 회차 활성 퀴즈셋의 문항마다 선택지별 답 수와 비율. 매칭 유형을 고르면 그 유형 퀴즈셋만 나온다.
- 성별·연령대·가입 주차 조건은 답한 회원에게 건다. 답이 없는 선택지도 0으로 나온다.
- 완주하지 않은 회원의 답도 센다. 중간에 그만둔 회원은 뒤 문항에 답이 없어서, 문항마다 응답 수가 줄어드는 것으로 이탈 지점도 보인다.
- 쏠림 기준선(몇 점부터 나쁜지)은 기획서에 없어 화면에 표시하지 않는다. 비율과 쏠림 점수만 보여 준다.
- 쏠림 점수: 문항마다 첫 선택지 비율이 50%에서 벗어난 %p(|첫 선택지 비율 − 50|)를 구한다. 문항 제목 옆에 문항 값이, 퀴즈셋 제목 옆에 답이 있는 문항끼리의 평균이 보인다. 선택지는 어드민 폼에서 둘로 고정이라 첫 선택지 하나로 본다.
- 성별 차이: 문항마다 성별로 첫 선택지를 고른 비율(인원)과 그 차이(%p). 두 성별 이상이 답한 문항에만 보여서, 성별 조건을 걸면 사라진다. 성별 미입력 회원은 차이에 넣지 않는다.
- 서버는 선택지마다 성별로 나눈 답 수를 내리고 합치기·비율은 `stats-calc.js`가 한다.

## 매칭

| 블록 | 세는 것 |
|---|---|
| 완주 | 퀴즈 참여 장의 회차 참여자와 같다 |
| 후보 | 1:1 은 `match_candidate`에 후보가 생긴 회원(`owner_member_id`). 그룹은 후보 그룹(`group_match_member`)에 들어간 회원이고 거절·미성사도 센다 |
| 성사 | 1:1 은 `personal_match.status = ACCEPTED`의 두 사람. 그룹은 성사된 그룹(`group_match.is_active`)을 수락(`ACCEPTED`)한 회원 |
| 매칭률 | 성사 ÷ 완주 |
| 1:1 후보 노출 쏠림 | 1:1 후보를 받은 회원마다 받은 후보 수(`match_candidate`의 `owner_member_id`별 행 수)를 1~5명·6~10명·11명 이상으로 묶고, 6명 이상 받은 회원 수와 비율. 회원마다 5명을 고르고 한쪽만 골라도 남기는 규칙(ADR 0040)이라 6명 이상은 여러 사람이 고른 회원이다. 회원 조건은 받은 회원에게 걸고, 받은 후보에는 관리자도 들어간다 |
| 연속 미매칭 회원 | 고른 주까지 최근 2회차를 모두 완주했는데 어느 회차에서도 성사되지 못한 회원(정지·탈퇴 제외, `ACTIVE`)의 닉네임·id. 성사는 위 성사와 같다. 앞 회차가 없으면 비운다. 매칭 배치(목요일 00:01) 전에는 이번 회차 성사가 없어 지난 회차 미매칭 완주자가 모두 잡히므로 목록 대신 안내만 보인다. 그룹은 금요일 00:00 마감까지 성사가 늘 수 있다. 매칭 유형을 고르면 그 유형 셋의 완주만 본다. 회차는 유형과 상관없이 활성 셋이 있는 주라, 그 유형 셋이 없는 회차가 끼면 목록이 빈다 |
| 미매칭 | 완주 − 성사. 그룹을 수락했지만 3명이 안 모인 회원도 미매칭이다. 성별 구성도 같은 뺄셈을 성별마다 한다 |
| 1:1 후보 점수 | 그 회차 후보 쌍의 `match_candidate.score`(0~100). 쌍은 두 방향 행으로 저장되어 `owner_member_id < other_member_id` 쪽만 센다. 평균·중앙값과 10점 구간, 옆에 성사(ACCEPTED) 쌍의 평균. 후보를 다시 만들면 이미 성사된 회원은 새 후보에서 빠져 그 쌍의 후보 행이 없어서, "점수 없는 성사 N쌍"으로 따로 보인다 |
| 그룹 후보 점수 | 그 회차 후보 그룹의 `group_match.score`. 평균·중앙값과 10점 구간, 옆에 성사(`is_active`) 그룹의 평균 |
| 성사 그룹 크기·성비 | 성사 그룹마다 수락(ACCEPTED)한 인원을 세서 인원수별 그룹 수와 성별 구성 |

- 기획서의 매칭은 "짝을 정해 준다"지만 디토는 후보를 보여 주고 신청·수락으로 성사된다. 그래서 매칭 = 성사로 세고 후보는 참고로 보여 준다(#329). 1:1 후보는 자격 상대만 있으면 누구나 받아서(ADR 0040) 거의 100%다.
- 배치는 목요일 00:01 에 돈다. 그 전에는 후보·성사가 0 이다. 그룹은 금요일 00:00 마감까지 성사가 늘 수 있다.
- 서버는 테이블마다 센 값만 내리고 후보·성사로 나누는 건 `stats-calc.js`가 한다. 1:1 성사는 한 행에 회원이 둘이라 `member_id_1` 쪽과 `member_id_2` 쪽을 따로 세서 내린다.
- 매칭 유형 조건은 화면이 그 유형 줄만 그려서 건다. 회원 조건은 세는 회원에게 건다.
- 성사 뒤 진행을 초기화하면 완주에서 빠져 성사가 완주보다 클 수 있다. 미매칭은 0 에서 자른다.
- 점수·그룹 크기는 쌍·그룹 단위라 회원 조건을 걸지 않고 관리자가 낀 쌍·그룹도 센다. 매칭 유형을 고르면 그 유형 카드만 보인다.
- 상위 20% 컷 라인은 지금 매칭 규칙(ADR 0040)에 없어 보여 주지 않는다.
- 점수·그룹 크기는 회원 조건을 걸지 않고 센다. 다만 표시는 장 단위라, 회원 조건을 걸어 완주자가 0 이면 장이 비면서 점수 카드도 함께 숨는다.

## 채팅

| 블록 | 세는 것 |
|---|---|
| 열린 방 | 그 회차의 방 중 `SCHEDULED`가 아닌 방(열린 방·끝난 방) |
| 대화 시작률 | 대화 메시지(`TEXT`·`IMAGE`, `SYSTEM` 제외)가 하나라도 있는 방 ÷ 열린 방 |
| 양방향 대화율 | 2명 이상이 대화 메시지를 보낸 방 ÷ 열린 방 |
| 전원 (그룹만) | 참여자(`chat_room_member`, 나간 사람 포함) 전원이 대화 메시지를 보낸 방 ÷ 열린 방. 두 사람 방은 양방향과 같아 비운다 |
| 방당 메시지 수 | 열린 방마다 대화 메시지 수. 메시지가 없는 방은 0개. 평균·중앙값과 구간(0, 1~9, 10~49, 50~99, 100개 이상) |
| 첫 메시지까지 | 대화가 있는 방에서 방이 열린 때(`opens_at`)부터 첫 대화 메시지까지 걸린 시간. 중앙값과 구간 |
| 첫 발신자 성별 | 대화가 있는 방에서 첫 대화 메시지를 보낸 사람의 성별 |
| 마지막 메시지 시점 | 방이 열린 때부터 마지막 대화 메시지까지를 첫째·둘째·셋째 날(24시간씩)로 나눈 것. 방은 열린 뒤 72시간이면 닫혀 "72시간 뒤"는 보통 비고, 시각을 옮긴 dev 에서 나온다 |

- 회차: 원본 매칭(1:1 `personal_match`, 그룹 `group_match`, 재매칭 `rematch`)의 퀴즈셋이 그 주 활성 셋인 방이다. 재매칭 방은 성사 뒤 처음 오는 금요일에 열리지만 원래 그룹의 퀴즈셋으로 센다. 그래서 매칭 장과 같은 회차에 잡히고 비활성 셋의 방은 빠진다.
- 관리자가 든 방도 센다. 방을 빼면 상대 회원의 대화까지 빠지고, 관리자만 빼면 1:1 방이 한 사람 방이 되기 때문이다(#329). 운영에서 팀원 계정이 관리자로 매칭에 참여하고 있어 다른 장과 숫자가 어긋날 수 있다.
- 재매칭은 따로 한 줄이고 1:1·그룹 합계에 넣지 않는다. 서로 원한 두 사람이라 수치가 높게 나온다. 매칭 유형 "전체"·"그룹"에서 보인다. 재매칭은 평가 제출로 뒤늦게 성사되어 지난 회차 숫자가 나중에 늘 수 있다.
- 방 단위 지표라 성별·연령대·가입 주차 조건은 걸지 않는다. 매칭 유형은 화면이 그 유형 줄만 골라 그린다.
- 메시지 지표는 대화 활동 합계처럼 재매칭 방을 넣지 않는다. 서버는 (방, 발신자 성별)마다 메시지 수와 첫·마지막 시각을 내린다. `stats-calc.js`가 방마다 행을 더하고, 첫 메시지가 가장 이른 행의 성별을 첫 발신자로 본다. 시각은 초 단위라 두 성별의 첫 메시지가 같은 1초 안이면 어느 쪽이 첫 발신자가 될지 정해져 있지 않다.
- 메시지 시각(`created_at`)은 실제 시각이고, 방 열린 시각(`opens_at`)은 회차 주 금요일 00:00 으로 정해진 값이다. 운영에서는 둘이 같은 시계라 맞지만, dev 에서 시간 조정으로 그 주말에 들어가 보내면 실제 시각이 찍혀 어긋난다. 실제 월~목에 보내면 음수가 되어 "열린 시각 전(시간 조정)" 칸에 따로 세고, 실제 다음 주에 지난 주말로 되돌려 보내면 값이 크게 나와 "48시간 넘게"·"72시간 뒤" 칸에 섞인다.
- 다른 장과 달리 서버가 방마다 한 행(참여자 수·보낸 사람 수)을 내린다. 방별 두 수를 묶은 분포는 쿼리가 복잡해져서, 비율은 `stats-calc.js`가 방 행으로 계산한다. 방 수는 회차당 많아야 수백이다.

## 평가

| 블록 | 세는 것 |
|---|---|
| 평가지 | 그 회차(`member_review.week_started_on`) 매칭에서 열린 평가. 한 사람 × 한 채팅방이 한 건 |
| 제출률 | 완료(`COMPLETED`) ÷ 평가지. 대상 일부만 답한 진행 중(`IN_PROGRESS`)은 제출로 치지 않고 따로 보여 준다 |
| 만남 상태·별점 | 응답(확정된 답, `review_answer.answered_at` 있음). 그룹은 대상마다 한 건이라 평가지보다 많다 |
| 상호 긍정 | 같은 방에서 서로를 평가한(둘 다 확정한) 두 사람을 한 쌍으로 세고, 둘 다 별점 4점 이상인 쌍의 비율. 쌍은 작성자 id 가 작은 쪽에서 한 번 센다. 1:1 은 방당 한 쌍, 그룹은 방 안 두 사람 쌍 전부 |
| 매칭 점수별 긍정 비율 | 확정된 답마다 그 매칭의 점수를 10점 구간으로 묶어 평가 수와 긍정(4점 이상) 비율. 1:1 은 작성자 → 대상 후보 점수(`match_candidate`), 그룹은 그룹 점수(`group_match.score`). 후보를 다시 만들어 후보 행이 없으면 "점수 없음" 칸 |

- 회원 조건은 평가를 쓴 사람에게 건다. 상호 긍정과 점수별 긍정 비율은 쌍·점수 단위라 회원 조건을 걸지 않고 관리자가 낀 평가도 센다(매칭 장 점수 카드와 같은 규칙).
- 평가 긍정은 별점 4점 이상이다(기획 확정). 만남 상태와 재매칭 의사는 보지 않는다.
- 매칭 유형 1:1 은 평가의 `PERSONAL`, 그룹은 `GROUP` 이다(`MatchingTypeFilter`). 재매칭 채팅은 평가를 열지 않아 없다.
- 프로필의 받은 평가 집계와 달리 방이 끝났는지는 보지 않는다. 열린 그룹 방에서 나간 사람이 먼저 낸 평가도 센다.

## 리텐션

- 주간 재참여율 = 지난 회차 참여자 중 고른 회차에도 완주한 회원 ÷ 지난 회차 참여자.
- 지난 회차는 고른 회차 바로 앞의 회차(활성 퀴즈셋이 있는 주)다. 퀴즈를 쉰 주는 건너뛴다. 앞에 회차가 없거나 고른 주가 회차가 아니면 비운다.
- 매칭 유형은 지난 회차에 푼 유형이다. 고른 회차에는 어느 유형이든 완주하면 다시 참여한 것으로 센다(1:1 → 그룹으로 옮긴 회원도 포함).
- 유형 전환(기획서의 "트랙 전환")은 다시 참여한 회원을 (지난 회차 유형 → 고른 회차 유형) 네 줄로 나눈다. 매칭 유형 조건은 지난 회차 쪽에만 걸려서, 하나를 고르면 그 유형에서 출발한 두 줄만 나온다.
- 회원 조건은 지난 회차 참여자에게 건다.
- 매칭 결과별 재참여: 지난 회차 완주자를 그 셋에서 성사됐는지로 나눈 재참여율. 성사는 매칭 장과 같다(1:1 ACCEPTED 쌍, 성사 그룹 수락자). 성사 안 됨은 전체에서 성사를 뺀 값이다.
- 평가 결과별 재참여: 지난 회차 평가에서 답을 하나 이상 확정한 회원을, 자기가 준 별점의 평균이 4점 이상(긍정)인지로 나눈 재참여율. 받은 평가가 아니라 준 평가로 나눠 "만족한 사람이 다시 오는가"를 본다.
  - 서버는 회원 id 없이 회원별 평균만 내린다.
  - 매칭 유형 조건은 평가의 유형(`PERSONAL`·`GROUP`)에 건다. 다시 참여는 유형과 상관없이 고른 회차 완주다.
  - 평가는 회차 다음 주 월요일에 열린다. 그래서 지난 회차 평가자는 고른 회차 퀴즈를 풀 무렵 평가를 낸다.
- 가입 주차별 리텐션(기획서의 "가입 코호트 잔존"): 고른 회차까지 8주 동안 가입한 회원을 가입한 주(월~일)로 묶고, 가입 뒤 N번째 회차에 퀴즈를 완주한 비율을 계단 표로 보인다. 가입한 주가 회차면 그 회차를 "가입한 주" 칸에 따로 둔다. 회차는 활성 퀴즈셋이 있는 주라 퀴즈를 쉰 주는 건너뛴다. 가입자 수는 회원 장의 주별 신규 가입과 같은 값이다(탈퇴한 회원 포함). 매칭 유형은 따지지 않는다.
- 지난 회차가 없으면(첫 회차, 회차 아닌 주) 장 전체가 비어 두 표도 보이지 않는다.

## 신고

- 고른 회차 주(월~일)에 접수(`member_report.created_at`)된 신고. 처리 상태는 지금 상태다.
- 접수 시각은 실제 시각이다. 시간 조정을 걸고 신고해도 실제 날짜의 주에 잡힌다.
- 평가일(월)에 낸 신고는 다음 회차 주에 잡힌다.
- 회원 조건은 피신고자에게 건다. 관리자가 피신고자인 신고는 기본으로 뺀다.
- 신고에는 어느 매칭에서 나왔는지가 없다. 매칭 유형을 고르면 피신고자가 그 회차에 그 유형 퀴즈셋을 완주한 신고만 센다. 한 주에 한 유형만 풀 수 있어 근사가 크게 어긋나지 않지만, 프로필 화면에서 들어온 신고처럼 매칭과 상관없는 신고도 섞인다. "전체"는 퀴즈에 참여하지 않은 피신고자의 신고도 센다.
- 사유는 여러 개 고를 수 있어 사유별 수를 더하면 신고 건수보다 클 수 있다.
- 신고 상위 회원: 고른 회차 주까지 4주(그 주 포함) 동안 접수된 신고를 피신고자마다 세서 2건 이상인 회원을 많은 순(같으면 id 순)으로 10명까지. 서버가 많은 순 10명을 내리고 2건 미만은 화면이 거른다.
  - 처리 상태와 상관없이 센다(기각된 신고도 들어간다). 접수 시각은 실제 시각이라, 시간 조정 중인 주를 고르면 위 주간 카드는 비어도 이 목록에는 실제 날짜의 신고가 보일 수 있다.
  - 4주에 걸쳐 보기 때문에 매칭 유형 조건은 걸지 않는다. 회원 조건은 건다.
  - 이 장의 다른 카드와 달리 그 주 신고가 없어도 보인다.
- 연속 미매칭·신고 상위 목록은 닉네임과 id 만 내리고, 닉네임을 누르면 회원 관리 검색(`#id`)으로 간다([ADR 0047](../adr/0047-admin-stats-member-lists.md)).

## 차단

화면에서는 신고와 한 장(신고·사용자 차단)이다. 사용자 차단 수 카드는 신고 숫자 줄에 있고 0이어도 보인다.

- 고른 회차 주(월~일)에 생긴(`member_block.created_at`) 차단 수. 차단 시각은 실제 시각이다.
- 차단을 풀면 행이 지워진다(`MemberBlockService.unblock`). 그래서 지금 유지 중인 차단만 센다. 해제를 소프트 삭제로 바꾸지 않기로 했다(#329). 해제가 잦아지면 다시 본다.
- 신고하면서 함께 건 차단(`UserReportService`)도 같은 테이블이라 센다.
- 회원 조건과 매칭 유형은 신고와 같이 차단된 사람에게 건다. 관리자가 차단된 건은 기본으로 뺀다.

## 알림

| 블록 | 세는 것 |
|---|---|
| 알림 건수 | 고른 회차 주(월~일, 실제 시각)의 푸시 발송 이력(`push_send_history`) 건수. 알림 한 건이 한 행이다 |
| 보냄·건너뜀 | 건너뛴 이유(`skip_reason`)가 비었으면 보낸 것, 있으면 건너뛴 것(채팅방 알림 꺼짐·알림 설정 꺼짐·등록된 기기 없음) |
| 실패율 | 보낸 알림 중 실패 기기가 하나라도 있는 알림 ÷ 보낸 알림 |
| 푸시 기기 등록률 | 퀴즈 참여 장의 활성 회원 중 지금 푸시 토큰(`member_device`)이 있는 회원 ÷ 활성 회원. 이력이 없는 주에도 보인다 |

- 이력은 기록을 시작한 뒤(#339 배포)부터 쌓여서 그 전 회차는 비어 있다. 180일 뒤 지워진다.
- 실패는 FCM 이 받지 않은 것이다. 폰에 실제로 떴는지는 모른다. iOS 알림 권한을 끈 기기도 보낸 것으로 센다.
- 새 메시지 알림은 알림 센터에서 안 읽은 이전 알림을 접어 한 줄로 보이지만, 이력은 보낼 때마다 한 행이라 메시지 수만큼 생긴다.
- 발송 건수는 매칭 유형과 관계가 없어 매칭 유형으로 거르지 않는다. 회원 조건은 받는 회원에게 건다. 관리자에게 간 알림은 기본으로 뺀다.
- 토큰 등록률의 분자는 지금 토큰 기준이지만 분모는 퀴즈 참여 장의 활성 회원이라 고른 회차에 따라 바뀌고 매칭 유형 조건도 같이 걸린다. 활성 회원이 없으면 "활성 회원 없음"이 보인다.
- 유형별 표는 보낸 알림이 많은 순이다. 실패 코드 `SEND_ERROR`는 FCM 에 넘기기 전에 서버에서 실패한 것이다.
- 알림 유형 라벨은 어드민 화면에서만 쓰여서 서버 enum 이 아니라 `stats-calc.js`에 둔다. 새 유형을 더하면 거기도 더한다. 빠지면 enum 이름 그대로 보인다.

## 손으로 대조하기

필터 없이 본 화면과 맞춰 보는 SQL 이다. 성별·연령대·가입 주차를 걸었으면 같은 조건을 `WHERE`에 더한다. 관리자 계정을 지표에 포함했으면 `role = 'USER'` 조건을 지운다. 단 쌍·점수·그룹 단위 지표(후보·그룹 점수, 그룹 크기, 상호 긍정, 점수별 긍정 비율)는 회원 조건을 걸지 않으니 그대로 둔다. 화면 날짜는 `M/D`라 연도는 고른 주를 기준으로 읽는다.

```sql
-- 상단 안내의 관리자 수 (계정 수, 고른 회차 완주자, 회원 조건 전)
SELECT COUNT(*) FROM member WHERE role = 'ADMIN' AND status <> 'LEFT';
SELECT COUNT(DISTINCT qp.member_id) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active AND qs.week_started_on = :monday
JOIN member m ON m.id = qp.member_id AND m.role = 'ADMIN'
WHERE qp.status = 'COMPLETED';

-- 누적 가입자·현재 회원·탈퇴·계정 상태 (PENDING 은 가입 미완료)
SELECT status, COUNT(*) FROM member
WHERE role = 'USER' AND (status = 'PENDING' OR joined_at IS NOT NULL)
GROUP BY status;

-- 계정 상태의 활성에 쓰는 회차 (:this_monday 는 실제 오늘이 아니라 서버 기준일의 월요일)
SELECT DISTINCT week_started_on FROM quiz_set
WHERE is_active AND week_started_on <= :this_monday
ORDER BY week_started_on DESC LIMIT 4;

-- 계정 상태의 활성 (:recent_rounds 는 위 회차들)
SELECT COUNT(DISTINCT qp.member_id) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
JOIN member m ON m.id = qp.member_id AND m.role = 'USER' AND m.status = 'ACTIVE' AND m.joined_at IS NOT NULL
WHERE qs.week_started_on IN (:recent_rounds) AND qp.status = 'COMPLETED';

-- 지역 (현재 회원)
SELECT location, COUNT(*) FROM member
WHERE role = 'USER' AND joined_at IS NOT NULL AND status <> 'LEFT'
GROUP BY location;

-- 탈퇴 사유 (고른 회차 주)
SELECT leave_reason, COUNT(*) FROM member
WHERE role = 'USER' AND joined_at IS NOT NULL AND left_at >= :monday AND left_at < :monday + INTERVAL 7 DAY
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

-- 문항 분포·성별 차이 (성별로 나뉜 행을 선택지마다 더하면 분포다)
SELECT q.display_order, q.question, c.content, m.gender, COUNT(m.id) FROM quiz_choice c
JOIN quiz q ON q.id = c.quiz_id
JOIN quiz_set qs ON qs.id = q.quiz_set_id AND qs.is_active
LEFT JOIN quiz_answer a ON a.choice_id = c.id
LEFT JOIN member m ON m.id = a.member_id AND m.role = 'USER'
WHERE qs.week_started_on = :monday
GROUP BY qs.id, q.id, c.id, m.gender
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

-- 1:1 후보 노출 쏠림 (화면 구간마다 회원 수. 6명 이상 = 6~10명 + 11명 이상)
SELECT CASE WHEN t.candidate_count <= 5 THEN '1~5명' WHEN t.candidate_count <= 10 THEN '6~10명'
    ELSE '11명 이상' END AS bin, COUNT(*)
FROM (SELECT c.owner_member_id, COUNT(*) AS candidate_count FROM match_candidate c
      JOIN quiz_set qs ON qs.id = c.quiz_set_id AND qs.is_active
      JOIN member m ON m.id = c.owner_member_id AND m.role = 'USER'
      WHERE qs.week_started_on = :monday
      GROUP BY c.owner_member_id) t
GROUP BY bin;

-- 연속 미매칭 회원 (:rounds 는 카드 기준 줄의 두 주 월요일. 매칭 유형을 골랐으면 AND qs.matching_type = ? 를 더한다)
SELECT m.id, m.nickname FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
JOIN member m ON m.id = qp.member_id AND m.role = 'USER' AND m.status = 'ACTIVE'
WHERE qs.week_started_on IN (:rounds) AND qp.status = 'COMPLETED'
  AND NOT EXISTS (SELECT 1 FROM personal_match pm WHERE pm.quiz_set_id = qp.quiz_set_id
                  AND pm.status = 'ACCEPTED' AND qp.member_id IN (pm.member_id_1, pm.member_id_2))
  AND NOT EXISTS (SELECT 1 FROM group_match_member gmm JOIN group_match gm ON gm.id = gmm.room_id AND gm.is_active
                  WHERE gm.quiz_set_id = qp.quiz_set_id AND gmm.member_id = qp.member_id
                  AND gmm.status = 'ACCEPTED')
GROUP BY m.id, m.nickname
HAVING COUNT(DISTINCT qs.week_started_on) = 2;

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

-- 1:1 후보 점수 (쌍마다 한 번) · 성사 쌍 점수
SELECT mc.score FROM match_candidate mc
JOIN quiz_set qs ON qs.id = mc.quiz_set_id AND qs.is_active AND qs.week_started_on = :monday
WHERE mc.owner_member_id < mc.other_member_id;
SELECT mc.score FROM personal_match pm
JOIN quiz_set qs ON qs.id = pm.quiz_set_id AND qs.is_active AND qs.week_started_on = :monday
JOIN match_candidate mc ON mc.quiz_set_id = pm.quiz_set_id
    AND mc.owner_member_id = pm.member_id_1 AND mc.other_member_id = pm.member_id_2
WHERE pm.status = 'ACCEPTED';

-- 그룹 후보 점수 · 성사 그룹 크기·성비
SELECT gm.is_active, gm.score FROM group_match gm
JOIN quiz_set qs ON qs.id = gm.quiz_set_id AND qs.is_active AND qs.week_started_on = :monday;
SELECT gm.id, m.gender, COUNT(*) FROM group_match_member gmm
JOIN group_match gm ON gm.id = gmm.room_id AND gm.is_active
JOIN quiz_set qs ON qs.id = gm.quiz_set_id AND qs.is_active AND qs.week_started_on = :monday
JOIN member m ON m.id = gmm.member_id
WHERE gmm.status = 'ACCEPTED'
GROUP BY gm.id, m.gender;

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

-- 방마다 발신자 성별로 대화 메시지 수·첫·마지막 시각 (서버가 내리는 모양, 재매칭은 화면에서 뺀다)
SELECT r.id, r.source_type, r.opens_at, m.gender, COUNT(*), MIN(msg.created_at), MAX(msg.created_at)
FROM chat_room r
LEFT JOIN personal_match pm ON r.source_type = 'PERSONAL' AND pm.id = r.source_id
LEFT JOIN group_match gm ON r.source_type = 'GROUP' AND gm.id = r.source_id
LEFT JOIN rematch rm ON r.source_type = 'REMATCH' AND rm.id = r.source_id
JOIN quiz_set qs ON qs.id = COALESCE(pm.quiz_set_id, gm.quiz_set_id, rm.quiz_set_id) AND qs.is_active
JOIN chat_message msg ON msg.room_id = r.id AND msg.message_type <> 'SYSTEM'
LEFT JOIN member m ON m.id = msg.sender_id
WHERE r.status <> 'SCHEDULED' AND qs.week_started_on = :monday
GROUP BY r.id, r.source_type, r.opens_at, m.gender;

-- 방 단위로 바로 보기 (메시지 0개 방 포함, 재매칭 제외, 첫·마지막 메시지까지 걸린 시간)
SELECT r.id, COUNT(msg.id) AS messages,
    TIMESTAMPDIFF(MINUTE, r.opens_at, MIN(msg.created_at)) / 60 AS first_message_hours,
    TIMESTAMPDIFF(MINUTE, r.opens_at, MAX(msg.created_at)) / 60 AS last_message_hours
FROM chat_room r
LEFT JOIN personal_match pm ON r.source_type = 'PERSONAL' AND pm.id = r.source_id
LEFT JOIN group_match gm ON r.source_type = 'GROUP' AND gm.id = r.source_id
JOIN quiz_set qs ON qs.id = COALESCE(pm.quiz_set_id, gm.quiz_set_id) AND qs.is_active
LEFT JOIN chat_message msg ON msg.room_id = r.id AND msg.message_type <> 'SYSTEM'
WHERE r.status <> 'SCHEDULED' AND r.source_type <> 'REMATCH' AND qs.week_started_on = :monday
GROUP BY r.id;

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

-- 상호 긍정 (서로 확정한 쌍과 둘 다 4점 이상인 쌍. 작성자 id 가 작은 쪽에서 한 번)
SELECT r.match_type, COUNT(*) AS pair_count, SUM(a.rating >= 4 AND ca.rating >= 4) AS both_positive
FROM review_answer a
JOIN member_review r ON r.id = a.member_review_id
JOIN member_review cr ON cr.chat_room_id = r.chat_room_id AND cr.author_member_id = a.reviewed_member_id
JOIN review_answer ca ON ca.member_review_id = cr.id AND ca.reviewed_member_id = r.author_member_id
    AND ca.answered_at IS NOT NULL
WHERE r.week_started_on = :monday AND a.answered_at IS NOT NULL AND r.author_member_id < a.reviewed_member_id
GROUP BY r.match_type;

-- 매칭 점수별 긍정 비율 (구간마다 평가 수와 긍정 수. 사이의 빈 구간은 화면만 0 으로 채운다)
SELECT CASE WHEN t.score IS NULL THEN '점수 없음' WHEN t.score >= 90 THEN '90점 이상'
    ELSE CONCAT(FLOOR(t.score / 10) * 10, '점대') END AS bin, COUNT(*) AS rating_count, SUM(t.rating >= 4) AS positive
FROM (SELECT a.rating, COALESCE(mc.score, gm.score) AS score FROM review_answer a
      JOIN member_review r ON r.id = a.member_review_id
      LEFT JOIN match_candidate mc ON r.match_type = 'PERSONAL' AND mc.quiz_set_id = r.quiz_set_id
          AND mc.owner_member_id = r.author_member_id AND mc.other_member_id = a.reviewed_member_id
      LEFT JOIN group_match gm ON r.match_type = 'GROUP' AND gm.id = r.match_id
      WHERE r.week_started_on = :monday AND a.answered_at IS NOT NULL) t
GROUP BY bin;

-- 주간 재참여·유형 전환 (:previous 는 지난 회차 월요일). 분모는 위 참여자 SQL 을 :previous 로 돌린 COMPLETED 수
SELECT qs.matching_type AS previous_type, again_set.matching_type AS again_type, COUNT(DISTINCT qp.member_id)
FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active AND qs.week_started_on = :previous
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
JOIN quiz_progress again ON again.member_id = qp.member_id AND again.status = 'COMPLETED'
JOIN quiz_set again_set ON again_set.id = again.quiz_set_id AND again_set.is_active AND again_set.week_started_on = :monday
WHERE qp.status = 'COMPLETED'
GROUP BY qs.matching_type, again_set.matching_type;

-- 매칭 결과별 재참여의 성사됨 (지난 회차 완주자 중 성사, 그중 고른 회차 완주는 위 SQL 에 같은 EXISTS 를 더한다)
SELECT qs.matching_type, COUNT(DISTINCT qp.member_id) FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active AND qs.week_started_on = :previous
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
WHERE qp.status = 'COMPLETED' AND (
    EXISTS (SELECT 1 FROM personal_match pm WHERE pm.quiz_set_id = qp.quiz_set_id AND pm.status = 'ACCEPTED'
            AND (pm.member_id_1 = qp.member_id OR pm.member_id_2 = qp.member_id))
    OR EXISTS (SELECT 1 FROM group_match_member gmm JOIN group_match gm ON gm.id = gmm.room_id AND gm.is_active
               WHERE gm.quiz_set_id = qp.quiz_set_id AND gmm.member_id = qp.member_id AND gmm.status = 'ACCEPTED'))
GROUP BY qs.matching_type;

-- 평가 결과별 재참여 (:previous 는 리텐션 장 기준 줄의 지난 회차 월요일. positive 1 = 평균 4점 이상 줄)
SELECT t.average_given >= 4 AS positive, COUNT(*) AS reviewer_count, SUM(t.completed_again) AS completed_again_count
FROM (SELECT r.author_member_id, AVG(a.rating) AS average_given,
          EXISTS (SELECT 1 FROM quiz_progress qp JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
                  WHERE qp.member_id = r.author_member_id AND qp.status = 'COMPLETED'
                  AND qs.week_started_on = :monday) AS completed_again
      FROM review_answer a
      JOIN member_review r ON r.id = a.member_review_id
      JOIN member m ON m.id = r.author_member_id AND m.role = 'USER'
      WHERE r.week_started_on = :previous AND a.answered_at IS NOT NULL
      GROUP BY r.author_member_id) t
GROUP BY positive;

-- 가입 주차별 리텐션의 가입자 (:first_monday 는 고른 회차 7주 전 월요일). 주별 신규 가입 표와 같다
SELECT DATE_SUB(DATE(joined_at), INTERVAL WEEKDAY(joined_at) DAY) AS join_monday, COUNT(*) FROM member
WHERE role = 'USER' AND joined_at >= :first_monday AND joined_at < :monday + INTERVAL 7 DAY
GROUP BY join_monday;

-- 가입 주차별 리텐션의 완주 (가입한 주와 그 뒤 회차만 칸에 들어간다)
SELECT DATE_SUB(DATE(m.joined_at), INTERVAL WEEKDAY(m.joined_at) DAY) AS join_monday, qs.week_started_on, COUNT(*)
FROM quiz_progress qp
JOIN quiz_set qs ON qs.id = qp.quiz_set_id AND qs.is_active
JOIN member m ON m.id = qp.member_id AND m.role = 'USER'
WHERE qp.status = 'COMPLETED'
  AND qs.week_started_on BETWEEN :first_monday AND :monday
  AND m.joined_at >= :first_monday AND m.joined_at < :monday + INTERVAL 7 DAY
  AND qs.week_started_on >= DATE_SUB(DATE(m.joined_at), INTERVAL WEEKDAY(m.joined_at) DAY)
GROUP BY join_monday, qs.week_started_on;

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

-- 신고 상위 회원 (고른 주까지 4주, 2건 이상, 많은 순 10명. 처리 상태와 매칭 유형은 거르지 않는다)
SELECT m.id, m.nickname, COUNT(*) AS report_count FROM member_report r
JOIN member m ON m.id = r.reported_member_id AND m.role = 'USER'
WHERE r.created_at >= :monday - INTERVAL 21 DAY AND r.created_at < :monday + INTERVAL 7 DAY
GROUP BY m.id, m.nickname
HAVING report_count >= 2
ORDER BY report_count DESC, m.id
LIMIT 10;

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

테스트 계정은 `USER` 계정을 쓴다. 어드민 로그인 계정(`ADMIN`)으로 앱을 쓰면 기본으로는 채팅과 쌍·그룹 단위 지표를 뺀 모든 장에서 빠지고, 필터에서 관리자 계정을 지표에 포함하면 들어간다.

| 장 | 쓸 도구 | 주의 |
|---|---|---|
| 회원 현황 | 더미 관리(지역은 무작위, 직접 만들기에서 고를 수 있다). 정지·영구 차단은 회원 상세의 직권 제재(`/admin/members/{id}/sanctions`)나 신고 검토 제재. 탈퇴 사유는 테스트 계정으로 앱에서 탈퇴 | 가입 시각은 실제 시각이라 일별 표에는 실제 오늘 줄에만 늘어난다. 시간 조정 중이면 실제 날짜의 주를 `?week=yyyy-MM-dd`로 연다(드롭다운에는 조정된 이번 주와 회차만 나온다). 탈퇴 시각은 조정된 시각이라 조정된 주에 잡힌다. 진행 중인 매칭·채팅이 있으면 탈퇴가 막히고, 탈퇴한 계정으로 30일 안에 다시 로그인하면 복구되어 사유가 빠진다. 앱은 사유를 늘 보내서 "사유 없음"·모르는 코드는 `POST /api/v1/users/{id}/leave`를 바디 없이나 아무 `reason`으로 불러야 생긴다. 더미를 탈퇴시키는 도구는 없어 탈퇴 한 건마다 소셜 계정이 하나 필요하다. 정지는 끝난 뒤에도 목요일 매칭 배치나 그 회원의 다음 로그인 전까지 SUSPENDED 로 남아 정지로 센다. 가입 미완료는 소셜 로그인만 하고 가입 화면을 닫으면 생기고, 성별·생년월일이 없어 회원 조건을 걸면 0명이 된다 |
| 퀴즈 참여 | 더미 관리(일괄은 늘 완주), 더미 직접 만들기의 "푼 문항 수" | 푼 문항 수 0 이면 진행 행이 없어 "시작"에도 안 잡힌다. 비활성 퀴즈셋에 만든 더미는 세지 않는다(더미 화면에 표시됨). 요일별 완주는 답 시각이 실제 시각이라 늘 실제 오늘 요일에만 쌓인다. 여러 요일은 실제 날짜가 다른 날에 만들어야 하고, 시간 조정으로 미래 회차를 풀면 회차 주보다 앞선 실제 요일이 찍힌다 |
| 문항 분포 | 더미 직접 만들기의 문항별 선택지 지정 | 일부만 푼 더미는 앞 문항에만 답이 있다. 예: 남성 더미 둘은 첫 선택지, 여성 더미 둘은 둘째 선택지를 고르면 성별 차이 100%p, 쏠림 0 이다 |
| 매칭 | 시간 조정을 그 회차 주(월~수)에 걸고 테스트 계정이 앱에서 퀴즈를 푼다 → 더미 관리에서 더미 만들기 → "퀴즈 마감 직후"로 옮겨 [정기 매칭 지금 돌리기](더미를 나중에 더했으면 퀴즈셋 매칭 다시 만들기) → QA 콘솔에서 1:1 신청·수락, 그룹 수락 | 1:1 은 상대가 실회원인 쌍만 콘솔이 다뤄서 테스트 계정이 같은 1:1 셋을 풀어 둬야 한다. 콘솔은 서버 시각 주의 퀴즈셋만 보여 준다. 그룹은 금요일 00:00 마감 전에 수락해야 하고 응답이 시작되면 다시 만들 수 없다. 목요일 매칭 전에는 후보·성사가 0 이다. 후보 점수는 같은 답을 고른 문항 수 ÷ 문항 수 × 100 이라, 더미 직접 만들기에서 문항별 선택지를 맞춰 원하는 점수대를 만든다(일괄 생성은 무작위). 회원마다 점수가 높은 5명을 고르고 상대가 고른 쌍도 남아(ADR 0040) 받은 후보는 5명을 넘을 수 있다. 인원이 많으면 낮은 점수 쌍은 생기지 않고, 점수를 바꾸면 퀴즈셋 매칭을 다시 만들어야 한다. 성사 그룹 크기는 QA 콘솔에서 한 그룹에 수락을 3명·4명…으로 넣어 만든다(3명째에 성사, 금요일 00:00 마감 전까지 더 붙는다). 성사 뒤 후보를 다시 만들면 그 쌍은 "점수 없는 성사"로 보인다. 후보 노출 쏠림의 6명 이상은 한 1:1 셋에 7명 이상을 넣어야 보인다. 연속 미매칭은 같은 회원이 연이은 두 회차를 풀어야 해서 테스트 계정으로 만든다(더미는 #333). 목요일 매칭 전에는 목록 대신 안내가 보인다 |
| 채팅 | 매칭 성사 → QA 콘솔 시간 조정 "채팅방 열린 직후" → 앱이나 QA 콘솔로 메시지 보내기 | 열리기 전(`SCHEDULED`) 방은 세지 않는다. 회차는 원본 매칭의 퀴즈셋이라 매칭 장과 같은 회차에 잡힌다. 재매칭 방은 성사 뒤 다음 금요일에 열린다. 재매칭 방 화면의 [옮기기]나 "채팅방 열린 직후"로 연 뒤 원래 그룹 회차를 연다. 방당 메시지 수는 방 화면 빠른 입력 "연속 35개"로 구간을 옮긴다(1번 10~49, 2번 50~99, 3번 100개 이상). 첫 발신자 성별은 원하는 성별의 더미로 먼저 보낸다. 첫 메시지까지·마지막 메시지 시점은 메시지 시각이 실제 시각이라, 실제 금~일에 그 주 활성 셋으로 성사된 방에서 시간 조정 없이 보낸 메시지로만 뜻이 있다. 시간 조정 버튼으로는 걸린 시간을 만들 수 없다 |
| 평가 | [정기 매칭 지금 돌리기] → QA 콘솔 시간 조정 "채팅방 열린 직후" → QA 콘솔에서 1:1 은 "채팅 종료", 그룹은 더미를 "나가기" → 더미로 평가 제출 | 그룹방은 채팅 종료가 없다. 나간 더미의 평가지만 바로 열리고, 1명만 남으면 방이 해체되어 전원 평가지가 열린다. 그룹방에서 상대 일부만 평가하면 "진행 중"이 된다. QA 콘솔 일괄 제출은 늘 5점이라 4점 미만은 대상마다 따로 제출하며 별점을 고른다. 상호 긍정은 두 사람이 모두 확정해야 세서, 1:1 은 테스트 계정도 앱에서 평가해야 한다 |
| 리텐션 | 테스트 계정으로 활성 퀴즈셋이 있는 연이은 두 회차에 시간 조정(각 월~수)을 걸고 앱에서 직접 풀기 | 사이에 다른 활성 셋 주가 끼면 그 주가 지난 회차가 된다. 미래 주를 쓰면 그 주 퀴즈셋을 먼저 만든다. 더미는 늘 새 회원이라 두 회차를 이어 풀 수 없다(#333). 유형 전환은 테스트 계정이 지난 회차와 고른 회차에 다른 유형을 풀어서 만든다. 두 회차 모두 두 유형의 활성 셋이 있어야 하고, 네 줄을 다 보려면 줄마다 계정이 하나씩 필요하다. 매칭 결과별 재참여는 테스트 계정이 지난 회차를 풀고 QA 콘솔에서 1:1 수락이나 그룹 수락으로 성사시킨 뒤 고른 회차를 다시 푼다. 가입 주차별 리텐션은 더미로도 만든다. 실제 이번 주에 만든 더미가 그 뒤 주의 활성 셋을 완주하면 "가입 뒤 N회차" 칸에 잡힌다. 가입한 주보다 앞선 셋으로 만든 더미의 완주는 어느 칸에도 들어가지 않는다. 평가 결과별 재참여의 다시 참여는 테스트 계정이 지난 회차 평가를 내고 고른 회차를 풀어야 생긴다. 4점 이상·미만 두 줄을 다 채우려면 계정이 둘 필요하다 |
| 차단 | QA 콘솔 신고에서 "더미가 이 회원도 사용자 차단"을 체크해 신고, 또는 앱에서 테스트 계정으로 차단 | 콘솔로 하면 신고 장도 같이 는다. 같은 상대에게 검토 대기 신고가 있으면 다시 신고할 수 없어 다른 더미로 한다. 이미 차단한 쌍이면 차단 수는 그대로다. 차단 시각이 실제 시각이라 시간 조정 중인 회차에는 안 잡힌다. 실제 날짜의 주를 `?week=`로 연다. 매칭 유형 조건까지 보려면 실제 날짜 주의 활성 셋으로 더미를 만든다 |
| 알림 | 테스트 계정(USER, 앱에 로그인해 푸시 토큰 등록)과 더미에게 알림이 가는 동작을 한다. 예: [정기 매칭 지금 돌리기], QA 콘솔에서 테스트 계정에게 1:1 신청, QA 콘솔 방에서 더미로 메시지 보내기, 공지 발송(`/admin/notices`, 정상 회원 전원에게 감). 퀴즈 오픈 알림은 실제 시각 월 00:00 배치에서만 나가서 재현되지 않는다 | 더미는 푸시 토큰이 없어 "등록된 기기 없음"으로 남는다. 방 알림 꺼짐: 테스트 계정이 앱에서 그 방 알림을 끄고 더미로 메시지를 보낸다(채팅 알림만 해당). 알림 설정 꺼짐: 앱 설정에서 채팅·매칭 알림을 끄고 더미 메시지나 1:1 신청을 받는다. 공지는 설정과 상관없이 늘 보내서 이 경우를 못 만든다. 방 알림을 먼저 보므로 둘 다 꺼져 있으면 방 알림 꺼짐으로 남는다. 실패: 테스트 계정으로 아무 문자열을 푸시 토큰으로 등록(`POST /api/v1/notifications/devices`)하면 FCM 이 거절해 실패로 남는다. 이 토큰은 자동으로 지워지지 않으니 끝나면 `DELETE /api/v1/notifications/devices/{token}`으로 지운다. 안 지우면 그 계정 알림이 계속 실패로 잡힌다. 로컬(`local` 프로필)은 가짜 발송기라 늘 성공으로 남아 실패를 재현할 수 없다. 이력 시각은 실제 시각이라 시간 조정 중인 회차에는 안 잡힌다. 실제 날짜의 주를 `?week=`로 연다 |
| 신고 | QA 콘솔에서 더미로 신고 | 접수 시각이 실제 시각이라 시간 조정 중인 회차에는 안 잡힌다. 실제 날짜의 주를 `?week=`로 연다. 신고 상위의 2건 이상은 서로 다른 더미로 신고한다(검토 대기 신고가 있으면 같은 더미로 다시 할 수 없다) |
| 관리자 계정 포함 | 더미 관리에서 완주 더미를 만들고(매칭까지 보려면 [정기 매칭 지금 돌리기]) → 회원 관리에서 `dummy-`로 찾아 그 더미의 권한을 관리자로 바꾼다 → 운영 지표 위 안내에 관리자 수가 뜨는지 보고, 필터에서 관리자 계정을 지표에 포함해 조회 | 포함하면 누적 가입자·퀴즈 완주·후보 받은 회원이 늘고, 채팅과 쌍·그룹 점수는 그대로다. 본인 계정 권한은 바꿀 수 없다. 더미 정리를 하면 관리자로 바꾼 더미도 함께 지워진다 |

더미 정리를 하면 더미의 진행·답·평가·신고와 함께 더미가 낀 방의 테스트 계정 평가, 테스트 계정이 더미를 신고한 건도 지워져 지난 회차 숫자가 바뀐다. 매칭·채팅·차단도 줄어든다. 더미가 낀 후보·1:1 매칭, 더미가 초대된 그룹(테스트 계정 초대까지), 그 매칭에서 나온 방, 더미가 하거나 받은 차단이 지워진다. 알림은 더미가 받은 발송 이력만 지워진다. 더미 때문에 테스트 계정이 받은 이력(더미의 메시지, 1:1 신청 등)은 테스트 계정 것이라 남는다. 푸시 기기 등록률은 분모에서 더미 완주가 빠져 값이 오른다.

## 알아 둘 것

- `joined_at`은 실제 시각으로 저장된다(`Member.register`). QA 시간 조정을 걸어도 새로 가입한 회원은 실제 날짜에 잡히므로, 회차 경계는 시간 조정만으로 재현되지 않는다.
- 탈퇴 30일 뒤 완전 삭제(`LeftMemberPurgeService`)를 켜면 그 회원은 누적·탈퇴·신규 숫자에서 모두 빠진다. 지금은 dry-run 이라 지워지지 않는다. 켜기 전에 회차 스냅샷을 정한다.
