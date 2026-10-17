# feedback 도메인

앱에서 회원이 보내는 버그 제보·개선 제안. 운영자가 확인만 하고 답변은 하지 않아서 문의(Inquiry)가 아니라 의견(Feedback)이라 부른다. 답을 기대하게 만드는 "이용 문의" 유형도 두지 않는다.

## 용어

- `Feedback`: 의견 1건. 보낸 회원, 유형, 내용, 기기 정보, 상태를 가진다.
- `FeedbackType`: 유형 enum. `code`는 FE와 주고받는 kebab-case 값.
- `FeedbackStatus`: 처리 상태.
- `FeedbackDeviceInfo`: 앱 버전·OS·기기 모델 묶음. FE가 모를 수 있어 전부 선택값이고, 엔티티에는 컬럼으로 펼쳐 저장한다.

## 불변식

- 로그인한 회원만 보낼 수 있다. 로그인 전 문제는 사업자 정보의 문의 메일로 받는다.
- 내용은 공백만으로 보낼 수 없다(`Feedback.receive`). 길이 제한(내용 `CONTENT_MAX_LENGTH` 1000자, 기기 정보 30/30/50자)은 신고와 같이 요청 DTO가 막는다.
- `FeedbackType`은 값 추가만 허용한다. 배포된 값의 이름/`code`는 바꾸거나 지우지 않는다. API는 `FeedbackType.from(code)`로 매핑하고, 없는 code는 들어온 값을 메시지에 담아 `BAD_REQUEST`로 거부한다.

## 상태 전이

- 지금은 `RECEIVED`(확인 대기)뿐이다.

## 후속

- 스크린샷 첨부: #310 (신고의 presigned 업로드 흐름 재사용)
- 어드민 목록·상세·처리 완료: #311

## 핵심 파일

- 엔티티: `domain/src/main/kotlin/com/ditto/domain/feedback/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/feedback/repository/FeedbackRepository.kt`
- API: `api/src/main/kotlin/com/ditto/api/feedback/`
