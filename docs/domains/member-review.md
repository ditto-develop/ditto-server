# member-review 도메인

평가(사용자 화면 용어) / review(코드·DB·API 용어). 채팅이 끝나면 참여자별로 열리고, 대상 회원 한 명씩 답변을 확정한다.
현재 범위는 데이터 모델과 생성까지 — 조회·제출 API, 채팅 종료 연동, 신뢰도·노쇼·공개 집계는 후속 이슈다.

## 용어

- `MemberReview`(진행 단위 — 한 회원 × 한 채팅 종료 건), `ReviewAnswer`(그 안의 평가 대상 한 명에 대한 응답).
- `ReviewProgressStatus`(진행 상태 — `NOT_STARTED`/`IN_PROGRESS`/`COMPLETED`), `MeetingStatus`(오프라인 만남 성사 여부 enum — `code` kebab-case 식별자).
- `EndedChatRoom`(채팅 종료 트랙이 넘기는 입력 계약 — `api/memberreview/dto/`).

## 명명 예약 규칙

**단독 `Review` 클래스는 금지한다.** 기존 신고 **검토**(`AdminReportReviewService`, `MemberReportStatus`, `REPORT_ALREADY_REVIEWED`)가 이미 review라는 말을 쓰고 있어 충돌한다.
진행 단위는 `MemberReview`, 개별 응답은 `ReviewAnswer`를 쓴다. "묶음 → 개별 응답" 구조는 기존 `Quiz` → `QuizAnswer` 선례를 따른 것이다.

## 불변식

- 진행 단위는 `(chat_room_id, author_member_id)` 유일 — 동일 종료 이벤트를 재처리해도 중복 생성되지 않는다. `MemberReviewService.createReviews`가 기존 건을 먼저 찾아 반환하고, DB 유일키가 최후 방어선이다.
- 평가 대상은 종료 시점 참여자 명단(`EndedChatRoom.participantIds`)에서 **자기 자신을 뺀** 회원들이다. 이후 멤버십이 바뀌어도 대상은 바뀌지 않는다.
- 참여자가 2명 미만이면 평가를 열 수 없다 (`INVALID_REVIEW_TARGET`) — 자기 자신이 빠지므로 대상이 0명이 된다.
- 응답 여부는 `answered_at` 하나로 표현한다(`NULL`이면 미응답). 대상별 제출이 최종 확정이라 상태가 둘뿐이고, 별도 상태 컬럼을 두면 같은 사실이 두 곳에 저장돼 어긋난다.
- 대상별 제출은 **최종**이다 — 이미 확정한 대상에 다시 답하면 `REVIEW_ALREADY_ANSWERED`로 거부한다. 수정·임시 저장 단계는 없다.
- 별점은 1~5 정수(`INVALID_REVIEW_ANSWER`), 코멘트는 공백 제거 후 `null` 정규화 + 최대 50자.
- 추천 태그는 두지 않는다 — 자유 코멘트만 받는다.
- `match_type`·`match_id`는 `chat_room`의 `(source_type, source_id)`와 같은 값이다. 조회마다 채팅방을 조인하지 않으려고 복사해 두며, 양쪽 다 생성 후 불변이라 어긋나지 않는다.
- 화면 노출 순서는 생성 순서와 같아 별도 순서 컬럼 없이 `id` 정렬로 처리한다.
- `MeetingStatus.NO_SHOW`는 노쇼 **신호**로만 보존한다 — 이 값 하나로 제재를 집행하지 않고, 운영자가 확정한 사건만 누적 대상이 된다.

## 상태 전이

```
NOT_STARTED → IN_PROGRESS → COMPLETED   (대상 응답이 확정될 때마다)
```

- 전이는 `MemberReview.recordAnswer(hasRemainingTarget, answeredAt)`가 수행한다. 남은 대상이 있으면 `IN_PROGRESS`, 마지막 대상이 확정되면 `COMPLETED` + `completed_at` 기록.
- **별도 전체 완료 API는 없다** — 마지막 대상 제출이 자동 완료시킨다.
- 대상이 하나뿐인 1:1은 첫 제출로 바로 `COMPLETED`가 된다.
- `status`는 자식 응답 수를 매번 집계하지 않고 목록 조회에서 바로 읽기 위한 **의도적 비정규화**다. `status`와 `completed_at`이 함께 움직여야 하므로 갱신은 `recordAnswer` 한 곳으로만 한다.

## 핵심 파일

- 엔티티: `domain/src/main/kotlin/com/ditto/domain/memberreview/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/memberreview/repository/`
- API·서비스: `api/src/main/kotlin/com/ditto/api/memberreview/`
- 마이그레이션: `domain/db/V20260726221757_리뷰 테이블 추가.sql`
- 설계 배경: [ADR 0011](../adr/0011-review-progress-and-answer-split.md)
