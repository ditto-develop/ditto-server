# ADR 0036 — 그룹 초대 응답 마감을 금요일 00:00으로 맞추고 미응답을 자동 거절한다

- 상태: Accepted (2026-10-02)
- 근거: Issue #237 (QA "그룹 채팅 끝까지 수락/거절 안 눌렀을 때 자동 거절"), 2026-10-02 결정

## Context

그룹 초대 응답 마감이 BE 안에 둘이었다.

- 미성사 알림(`UnformedGroupNotifier`)과 FE 화면은 그 주 금요일 00:00(채팅 개방)을 마감으로 본다. FE는 금요일부터 응답 모달을 띄우지 않는다.
- 수락·거절 API는 `MatchWeekPolicy.validateCurrentWeek`가 주차만 봐서 일요일 자정까지 받는다. [ADR 0026](0026-matching-scoped-to-operation-week.md)이 주차를 고정하며 생긴 결과다.

그래서 금요일에 "인원 미달" 알림을 받은 그룹이 토요일 수락(API 직접 호출)으로 성사돼 방이 열릴 수 있었다. 미응답(`PENDING`)을 처리하는 배치도 없었다.

#171과 ADR 0026은 기획서에 "일 23:59 응답 마감"이 있다고 인용하지만 원문은 확인하지 못했다. 일요일로 맞추는 안은 버렸다. 자동 거절이 월요일 주차 전환과 겹쳐 할 일이 없어지고, 미성사 알림과 FE 화면도 같이 옮겨야 한다.

## Decision

- 그룹 응답 마감은 그 주 금요일 00:00(채팅 개방)이다. `GroupResponseDeadline` 한 곳에 두고 응답 차단, 자동 거절, 미성사 알림이 같이 쓴다. 서버 시각(`ServerTimeProvider`, 어드민 오버라이드 포함)으로 본다.
- 마감 뒤 그룹 수락·거절은 `NOT_MATCHING_PERIOD`(5008)로 거부한다(`MatchWeekPolicy.validateGroupResponsePeriod`). 시각은 한 번만 읽고 마감은 퀴즈셋의 주를 기준으로 본다.
- `ChatRoomLifecycleScheduler`가 매분 마감이 가장 최근에 지난 한 주의 `PENDING`을 `DECLINED`로 바꾼다(`UnansweredGroupInvitationDecliner`). 직접 거절과 구분하지 않고 알리지 않는다. `status = PENDING` 조건부 UPDATE라 경계 시점의 수락을 덮지 않고, 대기 초대가 없으면 UPDATE를 내지 않는다.
- 한 주만 보는 이유는 서버 시각 오버라이드다. 범위를 넓히면 오버라이드를 미래 주로 옮길 때 실제 이번 주 초대가 되돌릴 수 없이 거절된다. 한 주는 마감 뒤 7일(금~다음 주 목) 동안 대상이라 그 사이 스케줄러가 한 번만 돌면 된다.
- 이전 주차에 남은 대기 초대는 소급 정리하지 않는다. 응답이 막혀 있고 성사 전 열람도 이번 주 퀴즈셋만 봐서 해가 없다.

## Consequences

- 얻음: 화면, 알림, API 마감이 하나가 된다. 미응답 초대가 끝난 상태로 정리된다.
- 비용: 응답 창이 목 05:00(후보 생성) ~ 금 00:00으로 확정된다(지금 화면 기준과 같다). 자동 거절된 사람은 금~일에 그 그룹 멤버의 성사 전 프로필을 볼 수 없다(`existsSharedCandidateGroup`이 DECLINED 제외).
- 남은 틈: 마감 직전에 시작해 금 00:00 뒤에 커밋되는 수락 한 건은 막지 못한다. 그 사이 미성사 알림이 먼저 나가면 알림 뒤에 성사될 수 있다. 이전부터 있던 틈이고 트랜잭션 하나 길이다.
- QA: 오버라이드를 금요일 이후로 옮기면 1분 안에 자동 거절되고 되돌릴 수 없다. 응답이 생긴 퀴즈셋은 후보 재생성도 거부되므로(5009) 같은 주 재테스트에는 새 퀴즈셋이 필요하다.
- ADR 0026의 "그룹 수락 마감이 사실상 일요일 자정"은 이 ADR로 바뀐다. 1:1 응답은 그대로 주차만 본다.

## Links

- 핵심 파일: `api/.../match/GroupResponseDeadline.kt`, `api/.../match/MatchWeekPolicy.kt`, `api/.../match/service/UnansweredGroupInvitationDecliner.kt`, `api/.../chat/scheduler/ChatRoomLifecycleScheduler.kt`
- 조회: `GroupMatchRepositoryImpl.findGroupMatchIdsByWeekStartedOn`, `GroupMatchMemberRepositoryImpl.declinePendingInvitations`
