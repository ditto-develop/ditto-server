# ADR 0009 — 제재 상태 이원화 (Sanction SSOT + Member 반영값, lazy 만료 판정)

- 상태: Accepted (2026-07-12)
- 근거: discussion (신고·제재 기능 설계 — 이슈 #96/#98)

## Context

신고·제재 기능(#96~)은 제재의 이력(차수 산정·기간·조치자·경위)과 집행(매 요청 접근 차단)을 모두 요구한다. 이력만 있으면 매 요청마다 제재 테이블을 조회해야 하고, 회원 상태 플래그만 있으면 차수 산정·감사 기록이 불가능하다. 또 기간 정지(2주)의 만료 해제를 어디서 수행할지 — 전용 스케줄러 vs 요청 시 판정 — 를 정해야 했다. 서버 시각은 어드민 오버라이드(`ServerTimeProvider`)가 존재해, 실제 시각 기반 스케줄러와 오버라이드 시각 기반 로직이 혼재하면 dev 검증이 어긋나는 제약이 있다.

## Decision

제재 상태를 이원화한다:

- **`sanction` 테이블이 SSOT** — 수위(level)·경위(origin)·기간·조치자·상태 전이(`ACTIVE → EXPIRED | LIFTED`)의 진실. 차수 산정은 이 이력에서 계산한다(FALSE_REPORT 제외).
- **`Member.status`(+`suspended_until`)는 집행용 반영값** — `JwtAuthenticationFilter`가 매 요청 Member를 이미 DB에서 로드하므로(ADR 0003·0006의 "DB가 인가 소스"), 추가 쿼리 없이 SUSPENDED/BANNED를 차단할 수 있다. 제재 적용·해제 시 두 저장소를 같은 트랜잭션에서 함께 갱신한다.

정지 만료는 **lazy 판정 + 기존 훅 원복**으로 처리한다:

- 인증 필터는 `suspended_until` 경과 시 통과만 시키고 저장하지 않는다 (인증 경로에 DB 쓰기 부수효과 금지).
- status 원복(`reinstate`)과 sanction의 `EXPIRED` 전이는 ⓐ 매칭 배치 시작부 일괄 처리, ⓑ 로그인 시점 개별 처리 — 두 기존 훅에서 수행한다. 전용 스케줄러는 만들지 않는다.

## Consequences

- 얻음: 매 요청 추가 쿼리 0으로 즉시 집행, 차수·감사 이력 보존, 시계 이원화 회피(판정 시각을 `ServerTimeProvider`로 통일 가능), 신규 스케줄러 없음.
- 비용: 두 저장소(sanction·member)의 정합을 적용/해제 트랜잭션이 책임져야 함 — 한쪽만 갱신하는 코드가 들어오면 상태가 어긋난다. 미로그인·비배치 구간에는 `Member.status`가 SUSPENDED로 남아 있을 수 있으나 필터가 만료를 lazy 판정하므로 실질 차단은 없다.
- 후속/연쇄: 어드민 검토(PR B)의 제재 적용 트랜잭션이 이 규칙(sanction 생성 + member 전이 + refresh 회수 동시 수행)을 따라야 한다. 탈퇴 부분 보존 전환(PR C) 시에도 sanction 이력은 비식별 보존 대상.

## Links

- 관련 이슈: #96(신고 접수), #98(제재 시스템)
- 핵심 파일: `domain/.../sanction/entity/Sanction.kt`(SSOT·전이), `domain/.../member/entity/Member.kt`(`suspendUntil`/`ban`/`reinstate`), `docs/domains/sanction.md`
