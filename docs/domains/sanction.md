# sanction 도메인

회원 제재 이력. 신고 검토(`/admin/reports/{id}/action`)와 어드민 직접 제재(`/admin/members/{id}/sanctions`)의 결과가 여기 쌓이고, 집행은 `Member.status` 반영값으로 수행된다 (ADR 0009). 적용·해제 공용 로직은 `api/admin/sanction/AdminSanctionService`, 해제·삭제 뒤 상태 재계산은 `MemberStatusRecalculator`(규칙은 `Member.alignStatusWith`).

## 용어

- `Sanction`(제재 1건 — 제재받은 회원·제재 근거·제재 종류·기간·처리자), `SanctionLevel`(제재 종류: WARNING/SUSPENSION/PERMANENT_BAN), `SanctionOrigin`(제재 근거: REPORTED/FALSE_REPORT/MANUAL), `SanctionStatus`(상태).
- MANUAL은 어드민 직접 제재(신고 없이 어드민이 거는 제재), LIFTED는 어드민이 해제한 제재다.
- 누적 제재(`countStrikes`): 같은 회원의 제재 수. 만료(EXPIRED)는 세고, 해제(LIFTED)와 허위 신고자 제재(FALSE_REPORT)는 세지 않는다.
- 차수: 누적 제재 수 + 1. 어드민 화면에 "이번이 N차 제재"로 보여 주는 **참고값**일 뿐 최종 제재 종류는 어드민이 정한다.

## 불변식

- 제재의 SSOT는 이 테이블 — `Member.status`/`suspended_until`은 집행용 반영값이며, 적용·해제는 **같은 트랜잭션**에서 둘을 함께 갱신한다.
- 영구 차단(PERMANENT_BAN)은 `ends_at`이 없어야 하고, 기간 제재(WARNING/SUSPENSION)는 `ends_at > starts_at` 필수 (`Sanction.impose`가 강제).
- 누적 제재(`SanctionRepository.countStrikes`)는 `origin = FALSE_REPORT`(허위 신고자 제재)와 `status = LIFTED`(어드민이 해제한 제재, 오처리 정정용)를 세지 않는다. 어드민 화면의 차수 = 이 값 + 1 (참고값일 뿐, 최종 제재 종류는 어드민이 정한다).
- 제재 기간(`AdminSanctionService.sanctionPeriod`): WARNING = 확정 시점 기준 차주 월요일 00:00부터 7일(일요일 23:59:59까지 차단과 동일 — endsAt은 exclusive 비교, 확정 주 잔여 참여 허용), SUSPENSION = 즉시부터 14일, PERMANENT_BAN = 종료 없음.
- WARNING(1차)은 `Member.status`·세션을 바꾸지 않는다 — 퀴즈 참여만 sanction 구간으로 차단.
- 어드민이 해제(lift)하거나 QA 더미 정리로 더미 신고에서 나온 제재를 지운 뒤에는 남은 제재 중 지금 적용 중인(ACTIVE이고 기간 안) 가장 무거운 제재로 `Member.status`를 재계산한다 (경고는 상태와 무관).
- 적용·해제·더미 정리는 회원 행을 먼저 잠그고(`MemberRepository.findWithLockById`) 제재를 바꾼다. 잠금 순서는 회원 → 제재이고, 여러 회원은 id 순이다. 재계산은 남은 제재도 잠금 읽기(`findAllWithLockByMemberIdAndStatus`)로 본다. 해제·정리 트랜잭션은 잠금 전에 비잠금 읽기가 있어, 잠그지 않으면 대기 중 커밋된 제재를 못 보기 때문이다(ADR 0011 규칙 7). 같은 수위가 여럿이면 가장 늦게 끝나는 제재를 따른다.
- `creator_name`은 처리자 표시명 스냅샷 — 어드민 계정이 삭제돼도 감사 기록이 남는다.

## 상태 전이

```
ACTIVE → EXPIRED   (기간 만료 — 매칭 배치·로그인 시 원복 흐름)
ACTIVE → LIFTED    (어드민이 해제 — 오처리 정정)
```

종결 상태는 불변, 전이는 ACTIVE에서만 (`expire`/`lift`).

## 핵심 파일

- 엔티티: `domain/src/main/kotlin/com/ditto/domain/sanction/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/sanction/repository/`
- 결정 배경: `docs/adr/0009-sanction-ssot-and-lazy-expiry.md`
- 신고(제재의 근거): `docs/domains/memberreport.md`
