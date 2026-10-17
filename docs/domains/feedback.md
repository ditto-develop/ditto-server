# feedback 도메인

앱에서 회원이 보내는 버그 제보·개선 제안. 운영자가 확인만 하고 답변은 하지 않아서 문의(Inquiry)가 아니라 의견(Feedback)이라 부른다. 답을 기대하게 만드는 "이용 문의" 유형도 두지 않는다.

## 용어

- `Feedback`: 의견 1건. 보낸 회원, 유형, 내용, 기기 정보, 상태를 가진다.
- `FeedbackType`: 유형 enum. `code`는 FE와 주고받는 kebab-case 값.
- `FeedbackStatus`: 처리 상태.
- `FeedbackDeviceInfo`: 앱 버전·OS·기기 모델 묶음. FE가 모를 수 있어 전부 선택값이고, 엔티티에는 컬럼으로 펼쳐 저장한다.
- `FeedbackImage`: 첨부 스크린샷. S3 비공개 버킷의 객체 키를 저장한다.

## 불변식

- 로그인한 회원만 보낼 수 있다. 로그인 전 문제는 사업자 정보의 문의 메일로 받는다.
- 내용은 공백만으로 보낼 수 없다(`Feedback.receive`). 길이 제한(내용 `CONTENT_MAX_LENGTH` 1000자, 기기 정보 30/30/50자)은 신고와 같이 요청 DTO가 막는다.
- `FeedbackType`은 값 추가만 허용한다. 배포된 값의 이름/`code`는 바꾸거나 지우지 않는다. API는 `FeedbackType.from(code)`로 매핑하고, 없는 code는 들어온 값을 메시지에 담아 `BAD_REQUEST`로 거부한다.
- 스크린샷은 의견당 최대 `MAX_COUNT`(3)장, 중복 키 금지 (`FeedbackImage.attachAll`). `(feedback_id, display_order)` 유니크.
- 스크린샷 키는 본인이 발급받아 업로드를 마친 `pending/feedbacks/{memberId}/` 키만 받는다 (`INVALID_ATTACHMENT_IMAGE_KEY`). 신고용 키(`pending/user-reports/`)도 거부한다.

## 스크린샷 업로드 (presigned)

신고와 같은 흐름이고 발급·검증·이동은 `api/src/main/kotlin/com/ditto/api/support/image/PendingImageStorage.kt`를 같이 쓴다.

1. `POST /api/v1/feedbacks/image-upload-urls`: presigned PUT URL 발급 (5MB 이하, 10분 유효)
2. FE가 S3에 직접 업로드. `Content-Type`과 본문 크기가 발급 때 보낸 값과 같아야 하고, 다르면 S3가 403을 돌려준다.
   ```
   curl -X PUT -H "Content-Type: image/png" --data-binary @shot.png "$UPLOAD_URL"
   ```
3. `POST /api/v1/feedbacks`에 `imageKeys`로 전달하면 서버가 업로드 여부를 확인하고 `pending/` → `feedbacks/`로 옮긴다

## 상태 전이

- 지금은 `RECEIVED`(확인 대기)뿐이다.

## 후속

- 어드민 목록·상세·처리 완료: #311

## 핵심 파일

- 엔티티: `domain/src/main/kotlin/com/ditto/domain/feedback/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/feedback/repository/`
- API: `api/src/main/kotlin/com/ditto/api/feedback/`
- 첨부 이미지 처리: `api/src/main/kotlin/com/ditto/api/support/image/`
