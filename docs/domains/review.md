# review 도메인

평가(사용자 화면 용어) / review(코드·DB·API 용어). 채팅이 끝나면 참여자별로 열리고, 대상 회원 한 명씩 답변을 확정한다.
현재 범위는 데이터 모델·생성과 **미완료 목록 조회·대상별 제출**까지 — 채팅 종료 연동, 신뢰도·노쇼·공개 집계는 후속 이슈다.

## 용어

- `MemberReview`(진행 단위 — 한 회원 × 한 채팅 종료 건), `ReviewAnswer`(그 안의 평가 대상 한 명에 대한 응답).
- `ReviewProgressStatus`(진행 상태 — `NOT_STARTED`/`IN_PROGRESS`/`COMPLETED`), `MeetingStatus`(오프라인 만남 성사 여부 enum — `Gender`처럼 enum 이름을 그대로 주고받는다).
- `EndedChatRoom`(채팅 종료 트랙이 넘기는 입력 계약 — `api/review/dto/`).

## 명명 예약 규칙

**단독 `Review` 클래스는 금지한다.** 기존 신고 **검토**(`AdminReportReviewService`, `MemberReportStatus`, `REPORT_ALREADY_REVIEWED`)가 이미 review라는 말을 쓰고 있어 충돌한다.
진행 단위는 `MemberReview`, 개별 응답은 `ReviewAnswer`를 쓴다. "묶음 → 개별 응답" 구조는 기존 `Quiz` → `QuizAnswer` 선례를 따른 것이다.

**API 표면도 엔티티 이름을 그대로 쓴다** — 경로 `/api/v1/member-reviews`, 응답 필드 `reviewId`·`matchType`·`matchId`. 계획서는 "리뷰 과제"(`review-tasks`, `taskId`, `sourceType`/`sourceId`)로 적혀 있으나, 새 어휘(`task`)를 늘리지 않고 엔티티·컬럼과 한 이름으로 맞추는 쪽을 택했다(2026-07-29). 같은 개념을 채팅 API가 `sourceType`으로 부르는 것과 갈리는 비용은 감수한다.

## 불변식

- 진행 단위는 `(chat_room_id, author_member_id)` 유일 — 동일 종료 이벤트를 재처리해도 중복 생성되지 않는다. `MemberReviewService.createReviews`가 기존 건을 먼저 찾아 반환하고, DB 유일키가 최후 방어선이다.
- 평가 대상은 종료 시점 참여자 명단(`EndedChatRoom.participantIds`)에서 **자기 자신을 뺀** 회원들이다. 이후 멤버십이 바뀌어도 대상은 바뀌지 않는다.
- 참여자가 2명 미만이면 평가를 열 수 없다 (`INVALID_REVIEW_TARGET`) — 자기 자신이 빠지므로 대상이 0명이 된다.
- 응답 여부는 `answered_at` 하나로 표현한다(`NULL`이면 미응답). 대상별 제출이 최종 확정이라 상태가 둘뿐이고, 별도 상태 컬럼을 두면 같은 사실이 두 곳에 저장돼 어긋난다.
- 대상별 제출은 **최종**이다 — 이미 확정한 대상에 다른 내용으로 답하면 `REVIEW_ANSWER_NOT_MODIFIABLE`로 거부한다(수정 시도와 "이미 답한 상태"를 코드로 구분한다). 수정·임시 저장 단계는 없다.
- **같은 내용의 재전송은 성공으로 되돌려준다**(멱등) — 네트워크 재시도로 사용자가 막히지 않게 한다. 비교 단위는 `ReviewAnswerContent`(만남 상태·별점·정규화된 코멘트)이며, **그룹 평가는 재매칭 의사까지 함께 비교한다** — 의사만 바꾼 재제출이 조용히 성공하면 사용자가 거절했다고 믿는 상대와 성사될 수 있다.
- 값 검증(별점 범위·코멘트 길이)은 재제출 분기보다 **먼저** 끝낸다 — 최초 제출과 재제출이 같은 오류로 답해야 한다.
- 이미 성사된 뒤의 재전송은 성사 결과를 **다시** 돌려준다 — 성사 응답이 유실됐을 수 있고, 성사는 양쪽이 모두 선택했다는 뜻이라 상대의 선택이 새로 드러나지 않는다.
- 제출은 평가 행을 잠근 뒤 진행한다 — 같은 평가에 겹친 제출(더블 탭·클라이언트 재시도·API 직접 호출)을 직렬화해 진행률·완료 전이가 유실되지 않게 한다. 겹친 요청은 서로의 커밋 전 상태를 보므로 멱등 판정만으로는 막히지 않고, 잠금이 순서를 만들어야 뒤 요청이 최신 상태를 보고 멱등 경로로 빠진다. 그룹 평가는 재매칭 행까지 잠그며 순서는 **평가 → 재매칭**으로 고정한다([ADR 0011](../adr/0011-rematch-pessimistic-lock.md)).
- 재매칭 반영은 `RematchSubmitter`가 맡는다 — 리뷰 서비스가 쌍 정규화 규칙과 재매칭 리포지토리를 직접 알지 않게 한다(`MatchAccessChecker`와 같은 결).
- 재매칭 의사는 **그룹 평가에만** 있다. 1:1 평가에 실려 오면 계약 위반으로 거부하고, 그룹 평가에 빠져 있어도 거부한다.
- 제출 응답의 재매칭 결과는 **이번 제출로 상호 성사된 경우에만** 채운다 — 상대가 아직 고르지 않은 것과 선택하지 않은 것을 응답으로 구분할 수 없어야 한다.
- 남의 평가에 대한 제출은 "권한 없음"이 아니라 `REVIEW_NOT_FOUND`로 답한다 — 존재 여부 자체를 알려주지 않는다.
- 별점은 1~5 정수(`INVALID_REVIEW_ANSWER`), 코멘트는 공백 제거 후 `null` 정규화 + 최대 50자.
- 추천 태그는 두지 않는다 — 자유 코멘트만 받는다.
- `match_type`·`match_id`는 `chat_room`의 `(source_type, source_id)`와 같은 값이다. 조회마다 채팅방을 조인하지 않으려고 복사해 두며, 양쪽 다 생성 후 불변이라 어긋나지 않는다.
- 화면 노출 순서는 생성 순서와 같아 별도 순서 컬럼 없이 `id` 정렬로 처리한다.
- **조회는 작성자 본인 것만 돌려준다.** 응답의 답변 값(`meetingStatus`·`rating`·`comment`·`answeredAt`)은 전부 요청자의 `member_review` 밑 행에서 오므로, 상대가 나를 어떻게 평가했는지는 어떤 경로로도 나가지 않는다.
- 미완료 목록 정렬은 `availableAt` 오래된 순이며, 같은 주의 채팅들이 같은 시각에 닫혀 값이 겹칠 수 있어 `id`로 순서를 고정한다.
- "미완료"는 `COMPLETED가 아님`이며, 이 판정은 조회 쿼리(`findPendingByAuthorOldestFirst`) 한 곳에만 둔다 — 서비스나 호출부에서 상태를 나열하지 않는다.
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

- 엔티티: `domain/src/main/kotlin/com/ditto/domain/review/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/review/repository/`
- API·서비스: `api/src/main/kotlin/com/ditto/api/review/`
- 마이그레이션: `domain/db/V20260726221757_리뷰 테이블 추가.sql`
- 설계 배경: [ADR 0011](../adr/0011-review-progress-and-answer-split.md)
