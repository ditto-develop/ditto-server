# ADR 0016 — 회원 탈퇴는 소프트 삭제로 처리하고 30일 안에 재가입하면 복구한다

- 상태: Accepted
- 근거: Issue #124, 피그마 `6.2.4 탈퇴` 화면 안내 문구, 기획 확인(2026-08-03)

## Context

탈퇴는 hard delete였다. `UserService.leaveUser`가 `RefreshToken`·`SocialAccount`·`Member`를 즉시 지웠다.

그 구현에는 부작용이 있었다. 제재(SUSPENDED/BANNED) 회원이 탈퇴하면 제재 이력과 재가입 식별 근거(`SocialAccount`)가 함께 사라져 **차단 우회 수단**이 된다. 그래서 제재 중 탈퇴를 아예 거부하는 가드(`CANNOT_LEAVE_WHILE_SANCTIONED`)를 뒀고, 코드 주석에 "탈퇴 부분 보존 전환(후속) 시 이 가드는 제거한다"고 남겨 뒀다.

한편 탈퇴 화면은 다른 정책을 안내하고 있었다.

- "진행 중인 매칭이나 채팅이 있으면 탈퇴가 제한됩니다" → 구현은 제재 중일 때만 거부
- "탈퇴 후 30일 이내 재가입하면 계정을 복구할 수 있습니다 / 30일이 지나면 모든 데이터가 완전히 삭제됩니다" → 구현은 즉시 완전 삭제
- 탈퇴 사유 선택(제출 필수) → 요청 바디를 받지 않아 사유가 유실

화면이 약속한 복구를 지키려면 즉시 삭제를 유지할 수 없다.

## Decision

**탈퇴 시점에는 아무것도 삭제하지 않는다.** `MemberStatus.LEFT`로 전이하고 `left_at`·`leave_reason`만 기록한다. 세션(refreshToken)은 즉시 끊고, `SocialAccount`는 남긴다.

**복구는 재가입이 곧 복구다.** `MemberSocialAccountService.findOrCreateMember`는 `SocialAccount`가 있으면 기존 `Member`를 반환하므로, 그 경로에 "LEFT이고 보존 기간 안이면 `restore()`" 분기만 더했다. 별도 복구 API는 두지 않는다 — 화면에도 그런 흐름이 없다.

**완전 삭제는 배치가 한다.** `LeftMemberPurgeService`가 `left_at + 30일`이 지난 회원을 지운다.

**제재 중 탈퇴 가드는 제거한다.** 소프트 삭제는 제재 이력과 `SocialAccount`를 모두 보존하므로 우회 수단이 되지 않는다. 예고했던 후속 작업이 이 ADR이다.

**진행 중 가드를 추가한다.** 진행 중인 매칭(`PENDING`/`ACCEPTED`)이나 끝나지 않은 채팅방(`SCHEDULED`/`ACTIVE`)이 있으면 `CANNOT_LEAVE_WHILE_IN_PROGRESS`로 거부한다.

**LEFT는 인증 게이트에서 막는다.** `JwtAuthenticationFilter`와 `AuthService.refresh` 양쪽에 넣는다 — refresh는 필터를 지나지 않으므로 한쪽만으로는 우회된다(제재 게이트와 같은 구조).

## Consequences

- 탈퇴 회원의 행이 최대 30일 남는다. 닉네임 유일키도 그 기간 점유된다 — 탈퇴자가 쓰던 닉네임을 다른 사람이 즉시 쓸 수 없다. 복구 시 닉네임이 살아 있어야 하므로 의도된 결과다.
- `left_at`이 지났는데 배치가 아직 돌지 않은 회원이 재로그인하면 복구하지 않는다. LEFT가 유지돼 인증 게이트가 막고, 배치가 정리한 뒤 새 회원으로 가입한다. 이 짧은 구간에서 로그인이 거부되는 것을 감수한다 — 복구 기한을 넘긴 사용자에게 기한을 늘려주는 것보다 낫다.
- 삭제 배치는 되돌릴 수 없다. `ditto.member.purge.dry-run`을 **기본 true**로 두어 운영 투입 전 대상만 로그로 관찰하고, `batch-limit`으로 1회 삭제량을 제한한다.
- 채팅의 "진행 중" 판정은 아직 끝나지 않은 방(`SCHEDULED`·`ACTIVE`) 기준이다. `SCHEDULED`(개방 예정, 재매칭 방)도 포함한다 — 상대가 곧 열릴 방을 기다리는 상태다. 종료(`ENDED`)된 방만 남은 회원은 탈퇴할 수 있다.
- **약관·개인정보 처리방침과 충돌한다.** 현행 문서는 "회원 탈퇴 시 원칙: 즉시 파기"(처리방침 4.1), "탈퇴 요청 시 즉시 처리"(이용약관 제7조), "탈퇴 요청 시 즉시 삭제"(처리방침 8.2)를 약속하고 있고, 예외는 "신고 접수·법적 분쟁 시 1년 보존"뿐이다. 30일 보관 근거 조항 추가와 공지가 별도로 정리돼야 한다(구현은 기획 결정에 따라 선행 진행).

## Links

- Issue #124
- [ADR 0009](0009-sanction-ssot-and-lazy-expiry.md) — 제재 SSOT·lazy 만료(게이트 구조를 여기서 따랐다)
- `docs/domains/member.md`, `docs/domains/auth.md`
