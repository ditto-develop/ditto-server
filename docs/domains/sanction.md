# sanction 도메인

회원 제재 이력. 신고 검토(어드민)·직권 조치의 결과가 여기 쌓이고, 집행은 `Member.status` 반영값으로 수행된다 (ADR 0009).

## 용어

- `Sanction`(제재 1건 — 피제재자·경위·수위·기간·조치자), `SanctionLevel`(수위: WARNING/SUSPENSION/PERMANENT_BAN), `SanctionOrigin`(경위: REPORTED/FALSE_REPORT/MANUAL), `SanctionStatus`(상태).
- 차수: 같은 회원의 유효 제재 수 + 1. 어드민 화면의 **추천값**일 뿐 최종 수위는 어드민이 확정한다.

## 불변식

- 제재의 SSOT는 이 테이블 — `Member.status`/`suspended_until`은 집행용 반영값이며, 적용·해제는 **같은 트랜잭션**에서 둘을 함께 갱신한다.
- 영구 차단(PERMANENT_BAN)은 `ends_at`이 없어야 하고, 기간 제재(WARNING/SUSPENSION)는 `ends_at > starts_at` 필수 (`Sanction.impose`가 강제).
- 차수 산정 시 `origin = FALSE_REPORT`(허위 신고자 제재)는 피신고 차수에 산입하지 않는다.
- WARNING(1차)은 `Member.status`를 바꾸지 않는다 — 퀴즈 참여만 sanction 조회로 차단. 차단 구간은 확정 시점 기준 차주 월요일 00:00~일요일 23:59:59 (datetime 저장, 주차 산술 금지).
- `creator_name`은 조치자 표시명 스냅샷 — 어드민 계정이 삭제돼도 감사 기록이 남는다.

## 상태 전이

```
ACTIVE → EXPIRED   (기간 만료 — 매칭 배치·로그인 시 원복 흐름)
ACTIVE → LIFTED    (어드민 직권 해제 — 오처리 정정)
```

종결 상태는 불변, 전이는 ACTIVE에서만 (`expire`/`lift`).

## 핵심 파일

- 엔티티: `domain/src/main/kotlin/com/ditto/domain/sanction/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/sanction/repository/`
- 결정 배경: `docs/adr/0009-sanction-ssot-and-lazy-expiry.md`
- 신고(제재의 근거): `docs/domains/memberreport.md`
