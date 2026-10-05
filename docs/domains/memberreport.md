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
- 사유는 **여러 개** 고를 수 있고 하나 이상이어야 한다(`reasons`, 콤마 구분 enum 이름 — 관심사와 같은 저장 방식). **대표 사유**(`reason`)는 선언 순서상 가장 앞, 즉 가장 심각한 것이다 — 내 제재 조회(`EffectiveSanctionResponse.reason`)는 계속 이 한 값을 쓴다. 요청은 `reasons[]`를 우선하고, 없으면 구버전 `reason` 하나를 받는다.
- ETC(기타)가 하나라도 섞이면 상세 설명(detail) 필수 (`REPORT_ETC_REASON_REQUIRED`).
- 어드민 목록·상세는 선택한 사유를 전부 보여 주고, 하나라도 심각 사유(`isSevere`)면 "심각 사유" 배지를 단다.
- detail은 `DETAIL_MAX_LENGTH`(500) 이하.
- 검토는 신고당 1회 — 종결 상태(ACTIONED/REJECTED/REJECTED_ABUSIVE)는 불변, 전이는 RECEIVED에서만. 전이는 `MemberReportRepository.completeReview`의 조건부 UPDATE(WHERE status=RECEIVED)가 강제한다 — 두 관리자가 동시에 처리해도 한쪽만 성공(`REPORT_ALREADY_REVIEWED`), 제재 중복 적용 불가.
- 검토 기한(`REVIEW_SLA`): 접수 후 24시간 안에 수동 검토 (기획). 어드민 목록·상세가 24시간이 지난 대기 건에 "24시간 지남"을 표시한다.
- 검토자 기록은 표시명 스냅샷(`reviewer_name`) — 어드민 계정이 삭제돼도 감사 기록이 남는다.
- 동일 (신고자, 피신고자) 쌍의 RECEIVED 신고가 있으면 재신고 불가 (`DUPLICATE_REPORT`).
- 이미지는 신고당 최대 `MAX_COUNT`(3)장·중복 키 금지 (`MemberReportImage.attachAll`이 강제), `(member_report_id, display_order)` 유니크.
- 이미지 키는 본인이 발급받아 업로드를 마친 `pending/user-reports/{memberId}/` 키만 접수 가능 (`INVALID_REPORT_IMAGE_KEY`), 접수 시 `user-reports/`(확정 영역)로 이동.
- enum(`Reason`/`Source`)은 값 추가만 허용 — 배포된 값의 이름/`code` 변경·삭제 금지. 채팅 출시 시 `Source.CHAT_ROOM` 추가 예정.

## 상태 전이

```
RECEIVED → ACTIONED | REJECTED | REJECTED_ABUSIVE   (어드민 검토 /admin/reports/{id}/action 에서만)
```

- ACTIONED(제재 적용)는 검토 결정(경고/2주 정지/영구 차단)에 따라 같은 트랜잭션에서 sanction 생성 + 회원 전이 + refresh 회수를 수행한다 — 규칙은 `docs/domains/sanction.md`.
- REJECTED_ABUSIVE(허위 신고로 기각)는 신고자를 자동으로 제재하지 않는다. 신고자 제재는 회원 제재 화면에서 어드민이 직접, 제재 근거(origin)를 FALSE_REPORT(허위 신고자 제재)로 골라 건다.

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
