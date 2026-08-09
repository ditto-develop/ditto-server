# member 도메인

> ⚠️ 이 도메인 코드를 수정하며 새 불변식·상태전이를 확인했으면 떠나기 전에 아래를 채워라.

회원과 그 속성. **골격 문서** — 불변식·상태전이는 회원 작업 시 코드 확인 후 채운다.

## 용어
`Member`, `MemberStatus`(상태 — PENDING/ACTIVE/SUSPENDED/BANNED/LEFT), `MemberRole`(역할), `Gender`/`GenderPreference`(성별·선호), `Job`, `Location`, `Interest`(관심사).

## 불변식
- 가입 완료(`register`)는 PENDING에서만 가능 — 제재(SUSPENDED/BANNED) 회원이 이 경로로 ACTIVE가 될 수 없다 (`INVALID_STATUS_TRANSITION`).
- `suspended_until`은 SUSPENDED일 때만 값이 존재한다 (`suspendUntil`이 설정, `ban`/`reinstate`가 비움).
- BANNED는 정지(`suspendUntil`)로 낮출 수 없다. 해제는 `reinstate`(어드민 직권)로만.
- `reinstate`는 SUSPENDED/BANNED에서만 호출 가능.
- 온보딩 필수 정보: 관심사·사는곳·직업·캐리커쳐는 가입 완료 시 항상 채운다 (`register`).
- `left_at`·`leave_reason`은 LEFT일 때만 값이 존재한다 (`leave`가 설정, `restore`가 비움).
- 탈퇴는 소프트 삭제다 — 데이터를 지우지 않고 LEFT로 전이하며, 완전 삭제는 30일 경과 후 배치가 한다 ([ADR 0016](../adr/0016-member-leave-soft-delete-and-restore.md)).
- 제재 중에도 탈퇴할 수 있다. 소프트 삭제가 제재 이력과 `SocialAccount`를 보존하므로 차단 우회가 되지 않는다.
- 진행 중인 매칭(PENDING/ACCEPTED)·끝나지 않은 채팅방·**성사됐는데 방이 아직 없는 재매칭**이 있으면 탈퇴할 수 없다(`LeaveProgressChecker`). 상대가 기다리는 상태를 남기지 않는다.
- 탈퇴는 **미성사 재매칭 쌍을 취소한다**(`CANCELLED(MEMBER_LEFT)`). 그대로 두면 남은 한쪽의 제출로 성사돼 탈퇴자와의 채팅방이 열린다. 상세는 [rematch 도메인](rematch.md).
- TODO: 역할(Role) 부여 규칙.

## 상태 전이

```
PENDING → ACTIVE                    (register — 가입 완료)
ACTIVE → SUSPENDED                  (suspendUntil — 기간 이용 정지, 2차 제재)
ACTIVE|SUSPENDED → BANNED           (ban — 영구 차단, 3차·중대 위반)
SUSPENDED|BANNED → ACTIVE           (reinstate — 정지 만료·어드민 직권 해제)
ACTIVE|SUSPENDED|BANNED → LEFT      (leave — 탈퇴, 소프트 삭제)
LEFT → ACTIVE                       (restore — 30일 내 재가입 시 복구)
```

- 1차 제재(경고)는 status를 바꾸지 않는다 — 퀴즈 참여만 sanction 조회로 차단.
- SUSPENDED의 만료 판정은 lazy(요청 시 `suspended_until` 경과 확인), status 원복은 매칭 배치·로그인 시. 배경: 신고·제재 계획 참조.
- 제재 상태의 이력·차수 SSOT는 `sanction` 도메인, `Member.status`는 매 요청 집행용 반영값.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/member/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/member/repository/`
- 소셜 로그인 연동은 `socialaccount` 도메인 참조.
