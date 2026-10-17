# feedback 도메인

앱에서 회원이 보내는 버그 제보·개선 제안. 운영자가 확인만 하고 **답변은 하지 않는다** — 그래서 "문의(Inquiry)"가 아니라 의견(Feedback)으로 부른다. 답을 기대하게 만드는 "이용 문의" 유형도 두지 않는다.

## 용어

- `Feedback`(의견 1건 — 보낸 회원·유형·내용·기기 정보·상태), `FeedbackType`(유형 enum — `code` kebab-case 식별자), `FeedbackStatus`(처리 상태).
- `FeedbackDeviceInfo`(앱 버전·OS·기기 모델 묶음). FE가 모를 수 있어 전부 선택값이고, 엔티티에는 컬럼으로 펼쳐 저장한다.

## 불변식

- 로그인한 회원만 보낼 수 있다. 로그인 전 문제는 사업자 정보의 문의 메일로 받는다.
- 내용은 공백만으로 보낼 수 없고 `CONTENT_MAX_LENGTH`(1000) 이하.
- `FeedbackType`은 값 추가만 허용 — 배포된 값의 이름/`code` 변경·삭제 금지. API는 `FeedbackType.from(code)`로 매핑(없는 code는 `BAD_REQUEST`).

## 상태 전이

- 지금은 `RECEIVED`(확인 대기)뿐이다. 어드민 확인·처리 완료는 #311 에서 추가한다.

## 후속

- 스크린샷 첨부: #310 (신고의 presigned 업로드 흐름 재사용)
- 어드민 목록·상세·처리 완료: #311

## 핵심 파일

- 엔티티: `domain/src/main/kotlin/com/ditto/domain/feedback/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/feedback/repository/FeedbackRepository.kt`
- API: `api/src/main/kotlin/com/ditto/api/feedback/`
