# intronote 도메인

소개노트(고정 질문에 대한 회원 답변). **골격 문서** — 불변식·상태전이는 소개노트 작업 시 코드 확인 후 채운다.

## 용어
`IntroNote`(회원이 한 질문에 단 답변), `IntroQuestion`(고정 질문 enum — `code` kebab-case 식별자 + `text` 화면 문구).

## 불변식
- `(member_id, question)` 유니크: 회원은 질문당 답변 1개만 가진다 (`intro_note_uk_1`).
- 답변 길이는 `ANSWER_MAX_LENGTH`(500) 이하. 빈 문자열 허용(부분 저장).
- `IntroQuestion`은 값 추가만 허용 — 배포된 값의 이름/`code` 변경·삭제 금지. API는 `IntroQuestion.from(code)`로 매핑(미존재 시 `BAD_REQUEST`).

## 열람 권한 (타인 조회)
`GET /api/v1/users/{id}/intro-notes`의 공개 범위는 두 회원의 관계로 갈린다 (`IntroNoteService.getIntroNotes`).

| 관계 | 공개 범위 |
|---|---|
| 본인 | 전체 |
| 매칭 성사(ACCEPTED)·같은 그룹 채팅 참여 (`MatchAccessChecker.isMatched`) | 전체 |
| 이번 주 매칭 후보 = 성사 전 (`MatchAccessChecker.isMatchCandidate`) | 미리보기 3문항 |
| 그 외 / 차단 관계 | `FORBIDDEN` |

- 미리보기 = 상대가 **작성한** 답변 중 무작위 2문항 + `ONE_WORD`(미작성이어도 항상 포함, 화면 마지막 칸 고정).
- 무작위 선택은 (조회자, 대상자) 기준 **결정적**이다 — 재조회로 문항이 바뀌면 반복 호출로 전체를 긁을 수 있다.
- 후보 판정의 기준 퀴즈셋은 후보 목록(`GET /api/v1/matches/1on1`·`/matches/group`)과 같다: 조회자가 **이번 운영 주에** 완주한 해당 타입 퀴즈셋(`MatchWeekPolicy`, [ADR 0026](../adr/0026-matching-scoped-to-operation-week.md)). 지난 주 후보 행이 남아 있어도 주가 바뀌면 닫힌다.
- `completedCount`는 "이 응답에 담긴 답변 중 작성된 수"다 — 미리보기에서는 최대 3.
- 근거 ADR: `docs/adr/0025-intro-note-candidate-preview.md`.

## 상태 전이
- 별도 상태 enum 없음. 답변은 `updateAnswer`로 갱신만 한다.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/intronote/entity/` (`IntroNote`, `IntroQuestion`)
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/intronote/repository/IntroNoteRepository.kt`
- API: `api/src/main/kotlin/com/ditto/api/intronote/`
