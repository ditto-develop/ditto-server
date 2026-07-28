# domain 모듈

도메인 레이어. 순수 Kotlin(`kotlin-convention`, Spring 의존성 없음).

## 담는 것

- Entity
- VO (Value Object)
- Repository (JPA, QueryDSL)

## 패키지 구조

도메인별 패키지 아래에 `entity/`, `repository/`, `repository/querydsl/`를 둔다.
현재 도메인: `member`, `quiz`, `match`, `review`, `rematch`, `chat`, `memberreport`, `sanction`, `intronote`, `socialaccount`, `refreshtoken`, `system`.

- QueryDSL 구현체는 `repository/querydsl/*RepositoryImpl.kt` 네이밍.

## DB 마이그레이션

- 위치: `domain/db/V{timestamp}_{설명}.sql` (Flyway 스타일).
- 파일명을 직접 짓지 말고 **반드시 `domain/db/create_sql.sh`로 생성**한다(타임스탬프 보장).
- 스키마 변경은 마이그레이션 + 관련 테스트를 동반한다.

## 도메인 지식

엔티티 뒤의 업무 규칙(용어·불변식·상태전이)은 코드가 아니라 `docs/domains/`에 둔다. 도메인 작업 전 해당 문서를 먼저 확인한다.
