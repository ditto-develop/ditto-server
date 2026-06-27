# ADR 0008 — 매칭 엔티티 유니크 모델링: 1:1 페어 정규화 + 그룹 참여/거절 테이블 분리

- 상태: Accepted
- 근거: commit-message (703c180 본문에 중복 문제·제약 기준 차이 명시)

> 사후 기록 — 커밋·코드·마이그레이션 SQL에서 재구성(2026-06-27).

## Context

1:1 매칭은 (퀴즈셋, 두 멤버) 조합이 방향과 무관하게 유일해야 하는데, 기존 (from, to, quizSet) UK는 A→B와 B→A를 서로 다른 레코드로 허용하는 중복 문제가 있었다. 그룹 매칭은 참여(JOINED)와 거절(DECLINED)의 유니크 기준이 다르다: 참여는 같은 방 같은 멤버 중복 불가(room_id, member_id)이지만 한 멤버가 같은 퀴즈셋의 여러 방에 참여할 수 있어 quizSet+member UK를 걸 수 없고, 거절은 같은 퀴즈셋 같은 멤버 중복 불가(quiz_set_id, member_id)다. 하나의 status 컬럼 테이블로는 두 제약을 동시에 표현할 수 없다.

## Decision

1:1(`PersonalMatch`)은 멤버 ID를 정규화해 memberId1=min, memberId2=max로 저장하고 requesterId를 별도 보존한다. UK를 (member_id_1, member_id_2, quiz_set_id)로 두어 방향 무관 중복을 DB 제약 하나로 막고, `receiverId()`/`counterpartOf()` 헬퍼로 방향을 복원한다.

그룹은 단일 GroupMatchParticipant 테이블을 `GroupMatchMember`(UK room_id+member_id)와 `GroupMatchDecline`(UK quiz_set_id+member_id)으로 분리하고, `GroupMatch`의 quizSetId UK는 제거해 퀴즈셋 1개에서 여러 소규모 그룹 생성을 허용한다.

## Consequences

- 얻음: 방향 무관 중복과 참여/거절 제약을 애플리케이션 체크 없이 DB UK로 강제, 퀴즈셋당 다수 그룹 허용.
- 비용: 1:1 조회 시 방향(requester/receiver)을 헬퍼로 복원해야 하고, 그룹 상태가 두 테이블에 분산되어 조회 시 조합 필요.

## Links

- commit: 703c180(페어 정규화 + GroupMatchParticipant 분리)
- `domain/.../match/entity/`(PersonalMatch member_id_1/2 + requesterId, GroupMatchMember, GroupMatchDecline), `domain/db/V20260419000000_매칭 테이블 추가.sql`(member_id_1/member_id_2 UK)
