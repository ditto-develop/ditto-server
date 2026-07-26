# ADR 0011 — 평가 데이터 모델: 진행 단위/응답 분리와 상태 표현 최소화

- 상태: Accepted
- 근거: discussion (이슈 #108 설계 논의, Figma 익스포트 `5.2 그룹 평가` 확인)

## Context

채팅이 끝나면 참여자마다 평가가 열리고, 1:1은 상대 1명·그룹은 자기 자신을 뺀 N-1명을 한 명씩 평가한다. 화면은 멤버 카드를 넘기며 진행률(`1/4` → `4/4`)을 보여주고, 이탈 후 재진입하면 이어서 작성해야 한다.

이 요구를 담으려면 "한 회원의 평가 진행"과 "대상 한 명에 대한 응답"이라는 두 층위가 필요하다. 진행률·완료 여부는 전자에, 별점·만남 여부·코멘트는 후자에 속한다.

초기 계획서 스키마는 여기에 상태 컬럼을 여럿 두고 있었다 — 진행 단위의 `status`, 응답의 `status`(`PENDING`/`ANSWERED`), 운영 무효화용 `validity_status`, 그리고 화면 순서용 `display_order`. 검토해 보니 뒤의 셋은 각각 `answered_at`, `invalidated_at`, `id`가 이미 말하고 있는 사실이었다.

원본 매칭 식별도 문제였다. 계획서는 리뷰 전용 `PERSONAL_MATCH / GROUP_MATCH / GROUP_REMATCH` enum을 상정했는데, `chat_room`이 이미 `(source_type, source_id)`로 같은 값을 담고 있다. `ChatRoom.sourceId`가 그대로 `PersonalMatch.id`이므로 값도 의미도 동일하다.

## Decision

**두 층위로 나눈다.** `MemberReview`(진행 단위, `(chat_room_id, author_member_id)` 유일)와 `ReviewAnswer`(대상별 응답, `(member_review_id, reviewed_member_id)` 유일). 연결은 이 저장소 관례대로 JPA 연관관계 없이 ID 컬럼으로만 한다.

**상태는 이미 그 사실을 말하는 값이 있으면 따로 두지 않는다.** 응답 여부는 `answered_at`(`NULL`이면 미응답), 화면 순서는 `id` 정렬로 처리한다. 운영 무효화는 이 범위에 소비자가 없어 `O2`(무효화 API)에서 `invalidated_at`과 함께 도입한다.

예외는 진행 단위의 `status`다. 자식 응답 수를 매번 집계하지 않고 미완료 목록을 인덱스로 거르기 위해 **의도적으로 중복 저장**하며, `status`와 `completed_at`이 함께 움직이도록 갱신 경로를 `MemberReview.recordAnswer` 하나로 좁힌다.

**매칭 식별은 `ChatRoomType`을 재사용한다.** 별도 enum을 만들면 `GROUP_REMATCH`가 추가될 때 두 곳을 맞춰야 한다. 다만 컬럼명은 `match_type`·`match_id`로 두어 이름만으로 매칭 ID임이 드러나게 한다(`chat_room`의 `source_id`는 주석을 읽어야 알 수 있었다). 이 값은 `chat_room`과 중복이지만 조회마다 조인하지 않으려는 비정규화이며, 양쪽 다 생성 후 불변이라 어긋나지 않는다.

**대상별 제출은 최종이다.** Figma 익스포트에서 첫 멤버·마지막 멤버 화면 모두 뒤로가기 UI가 없고, 마지막 대상(`4/4`)에서도 버튼 문구가 `다음 멤버 평가하기`로 같으며, `이미 평가 완료` 상태가 따로 존재한다. 수정·임시 저장 단계를 두지 않고, 마지막 대상 제출이 진행 단위를 자동 완료시킨다.

**생성은 멱등이다.** 종료 이벤트가 재전달되거나 누락 복구가 돌아도 평가가 중복 생성되지 않아야 하므로, 참여자 단위로 기존 진행 단위를 먼저 찾아 반환하고 DB 유일키를 최후 방어선으로 둔다.

## Consequences

- 얻음: 같은 사실이 두 컬럼에 저장돼 어긋나는 경로가 없다. 종료 이벤트 재처리가 안전하다. `GROUP_REMATCH` 추가 시 enum 한 곳만 고치면 된다. 진행률·미완료 목록을 조인 없이 읽는다.
- 비용: 진행 단위 `status`는 파생 가능한 값을 저장하므로 갱신 경로를 좁게 유지해야 한다. `match_type`·`match_id`는 `chat_room`과 중복이라 "왜 두 번 저장하나"라는 질문이 반복될 수 있어 근거를 엔티티·마이그레이션 주석에 남겼다.
- 후속: 수정 UX가 필요해지면 임시 저장(`saved_at`)과 최종 확정을 분리하는 별도 결정이 필요하다. 운영 무효화 컬럼은 `O2`에서 추가한다.

## Links

- issue #108
- `domain/src/main/kotlin/com/ditto/domain/memberreview/entity/`(MemberReview, ReviewAnswer)
- `api/src/main/kotlin/com/ditto/api/memberreview/`(EndedChatRoom, MemberReviewService)
- `domain/db/V20260726221757_리뷰 테이블 추가.sql`
- [ADR 0008](0008-matching-entity-uniqueness-modeling.md)(유일키로 중복을 막는 같은 접근)
