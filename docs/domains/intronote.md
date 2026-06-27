# intronote 도메인

소개노트(고정 질문에 대한 회원 답변). **골격 문서** — 불변식·상태전이는 소개노트 작업 시 코드 확인 후 채운다.

## 용어
`IntroNote`(회원이 한 질문에 단 답변), `IntroQuestion`(고정 질문 enum — `code` kebab-case 식별자 + `text` 화면 문구).

## 불변식
- `(member_id, question)` 유니크: 회원은 질문당 답변 1개만 가진다 (`intro_note_uk_1`).
- 답변 길이는 `ANSWER_MAX_LENGTH`(500) 이하. 빈 문자열 허용(부분 저장).
- `IntroQuestion`은 값 추가만 허용 — 배포된 값의 이름/`code` 변경·삭제 금지. API는 `IntroQuestion.from(code)`로 매핑(미존재 시 `BAD_REQUEST`).

## 상태 전이
- 별도 상태 enum 없음. 답변은 `updateAnswer`로 갱신만 한다.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/intronote/entity/` (`IntroNote`, `IntroQuestion`)
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/intronote/repository/IntroNoteRepository.kt`
- API: `api/src/main/kotlin/com/ditto/api/intronote/`
