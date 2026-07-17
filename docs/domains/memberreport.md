# memberreport 도메인

회원 신고(회원이 상대 회원을 신고). 어드민 검토·제재 적용은 후속 이슈에서 확장 예정.

## 용어

- `MemberReport`(신고 1건 — 신고자·피신고자·사유·접수 위치·상세·상태), `MemberReportImage`(신고 첨부 이미지 — S3 객체 키).
- `MemberReportReason`(신고 사유 enum — `code` kebab-case 식별자, ETC는 `requiresDetail`), `MemberReportSource`(접수 위치 enum), `MemberReportStatus`(처리 상태).
- API 표면에서는 **user-report**로 부른다 (`/api/v1/user-reports`, `UserReportController`) — 도메인 member ↔ API user 이원 어휘는 Member/`/users/me` 선례를 따른 것.

## 명명 예약 규칙

**접두어 없는 bare `Report` 클래스는 금지한다.** 신고는 `MemberReport`(API 표면 user-report), 문서·통계 기능이 생기면 `Summary`/`Statistics` 등 다른 이름을 쓴다 — "결과 리포트(보고서)"와의 용어 충돌 방지.

## 불변식

- 자기 자신 신고 금지 (`MemberReport.receive`가 `CANNOT_REPORT_SELF`로 거부).
- ETC(기타) 사유는 상세 설명(detail) 필수 (`REPORT_ETC_REASON_REQUIRED`).
- detail은 `DETAIL_MAX_LENGTH`(500) 이하.
- 검토는 신고당 1회 — 종결 상태(ACTIONED/REJECTED/REJECTED_ABUSIVE)는 불변, 전이는 RECEIVED에서만.
- 동일 (신고자, 피신고자) 쌍의 RECEIVED 신고가 있으면 재신고 불가 (`DUPLICATE_REPORT`).
- 이미지는 신고당 최대 `MAX_COUNT`(3)장·중복 키 금지 (`MemberReportImage.attachAll`이 강제), `(member_report_id, display_order)` 유니크.
- 이미지 키는 본인이 발급받아 업로드를 마친 `pending/user-reports/{memberId}/` 키만 접수 가능 (`INVALID_REPORT_IMAGE_KEY`), 접수 시 `user-reports/`(확정 영역)로 이동.
- enum(`Reason`/`Source`)은 값 추가만 허용 — 배포된 값의 이름/`code` 변경·삭제 금지. 채팅 출시 시 `Source.CHAT_ROOM` 추가 예정.

## 상태 전이

```
RECEIVED → ACTIONED | REJECTED | REJECTED_ABUSIVE   (어드민 검토에서만, 후속 이슈)
```

## 이미지 업로드 (presigned)

1. `POST /api/v1/user-reports/image-upload-urls` — 서버가 presigned PUT URL 발급 (크기·타입 서명 포함, 10분 유효)
2. FE가 S3에 직접 업로드 (서버 미경유)
3. `POST /api/v1/user-reports` — objectKey 전달, 서버가 `exists` 검증 후 `pending/ → user-reports/` 이동

미접수 업로드는 S3 라이프사이클 규칙이 `pending/` 접두사 기준으로 삭제한다(버킷 설정 — 인프라 작업).

## 핵심 파일

- 엔티티: `domain/src/main/kotlin/com/ditto/domain/memberreport/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/memberreport/repository/`
- API: `api/src/main/kotlin/com/ditto/api/userreport/`
- 스토리지: `infrastructure/src/main/kotlin/com/ditto/infrastructure/storage/`
