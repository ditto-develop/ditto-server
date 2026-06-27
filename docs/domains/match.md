# match 도메인

매칭(1:1 / 그룹). **골격 문서** — 불변식·상태전이는 매칭 작업 시 코드 확인 후 채운다.

## 용어
`MatchCandidate`(후보), `PersonalMatch`(1:1), `GroupMatch`(그룹), `GroupMatchMember`(그룹 구성원), `GroupMatchDecline`(그룹 매칭 거절).

## 불변식
- TODO: 후보→매칭 생성, 중복 매칭 방지, 그룹 거절 처리 규칙을 코드 확인 후 기술.

## 상태 전이
- 상태 enum: `match/entity/PersonalMatchStatus`(1:1). 그룹은 `GroupMatchDecline` 흐름.
- TODO: 실제 전이(생성→…→완료/거절)를 서비스 로직 확인 후 명시.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/match/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/match/repository/` (+ `querydsl/`)
