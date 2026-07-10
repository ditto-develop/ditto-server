# member 도메인

> ⚠️ 이 도메인 코드를 수정하며 새 불변식·상태전이를 확인했으면 떠나기 전에 아래를 채워라.

회원과 그 속성. **골격 문서** — 불변식·상태전이는 회원 작업 시 코드 확인 후 채운다.

## 용어
`Member`, `MemberStatus`(상태), `MemberRole`(역할), `Gender`/`GenderPreference`(성별·선호), `Job`, `Location`, `Interest`(관심사).

## 불변식
- TODO: 온보딩 필수 정보·상태 전이 조건·역할 부여 규칙을 코드 확인 후 기술.

## 상태 전이
- 상태 enum: `member/entity/MemberStatus`.
- TODO: 가입→온보딩→활성 등 전이를 서비스 로직 확인 후 명시.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/member/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/member/repository/`
- 소셜 로그인 연동은 `socialaccount` 도메인 참조.
