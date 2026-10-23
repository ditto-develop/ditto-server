# ADR 0046 — 어드민 운영 지표는 서버가 집계 결과만 넘기고 화면 JS 가 계산해 그린다

- 상태: Accepted (2026-10-22)
- 근거: code-evidence (#328)

## Context

기획 "디토 어드민 운영 지표 정의서"의 지표를 어드민(`/admin/stats`)에 붙인다. 장이 유저 현황·퀴즈 참여·문항 분포·평가·리텐션·신고로 여럿이고, 장마다 비율·0 채우기·구간 묶기·라벨 같은 표현용 계산이 붙는다.

처음에는 이 계산을 Kotlin 화면 DTO 와 가공 함수로 만들었는데, 어드민 기능 때문에 서버 구조가 커진다는 판단이 나왔다. 어드민은 Thymeleaf 서버 렌더지만 화면별 JS(`static/admin/js/`)를 이미 쓴다.

## Decision

- 서버는 필터를 쿼리 조건으로 바꿔 DB 집계를 돌리고, 결과를 그대로 `StatsPageData` JSON 으로 페이지에 넣는다(`th:inline="javascript"`).
- 비율·퍼센트, 0 채우기, 요일·주 라벨, 생년월일 → 연령대 묶기는 `stats-calc.js`(DOM 없는 순수 계산)가 하고, `stats.js`가 그린다.
- 필터 폼은 서버가 그린다. JS 없이도 조건을 바꿔 다시 조회할 수 있다.
- 필터는 SQL `WHERE`에 들어가야 해서 연령대 → 생년월일 범위 변환은 서버(`AgeBand`)에 둔다. 화면이 같은 구간으로 묶을 수 있게 구간 정의는 JSON(`ageBandRanges`)으로 내려 한 곳에서만 정의한다.
- 지표 전용 개념(연령대·매칭 유형 필터)은 domain 에 두지 않는다. domain 은 회원 조건 `MemberFilter`(성별·생년월일 범위·가입 시각 범위)만 받는다. 관리자 계정은 늘 뺀다.
- 회원 행을 그대로 브라우저에 보내지 않는다. 개인정보가 나가고 양도 커져서 세는 일은 DB 에서 한다. 운영 개입용 목록만 예외다([ADR 0047](0047-admin-stats-member-lists.md)).

## Consequences

- 장을 하나 더할 때 서버는 쿼리와 `StatsPageData` 필드만 는다. 화면용 Kotlin DTO·가공 함수는 생기지 않는다.
- 계산 JS 는 `jsTest`(`node --test`)로 검증한다. 빌드에 Node 가 필요해졌다([build](../modules/build.md)). DOM 코드는 테스트가 없어 렌더링해 보고 확인한다.
- 만 나이를 서버(생년월일 범위)와 JS(나이 계산)가 따로 구한다. 둘의 경계가 같아야 해서 양쪽 테스트에 경계 사례를 둔다.
- JSON 은 Thymeleaf 가 JS 문자열로 이스케이프해 넣는다. 관리자가 쓴 문구가 들어가도 `</script>`로 끊기지 않는다. 화면은 `textContent`로만 그린다.
- 숫자는 조회 시점 DB 상태다. 회차 스냅샷이 없어 탈퇴 30일 뒤 완전 삭제된 회원은 지난 회차 숫자에서도 빠진다.

## Links

- 지표 정의와 대조 SQL: [admin-stats](../admin/stats.md)
- JS 테스트: [build](../modules/build.md)
