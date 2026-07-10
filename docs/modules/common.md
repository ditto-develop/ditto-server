# common 모듈

ditto 비즈니스 로직과 **최대한 무관한 공통 기반 코드**만 둔다. 순수 Kotlin(`kotlin-convention`, Spring 의존성 없음).

## 담는 것

- util 클래스
- JSON converter / 직렬화 설정 (`ObjectMapperFactory`, `DateTimeFormats`)
- 예외 기반구조 (`WarnException`, `ErrorException`, `ErrorCode`)
- API 공통 응답 래퍼 (`ApiResponse`)
- `@Loggable` 어노테이션

> 비즈니스(도메인) 로직은 넣지 않는다. 그건 `domain`/`api`로.

## 의존성

`common`은 최하단 — 다른 모듈에 의존하지 않는다(`domain`·`infrastructure`·`api`가 `common`에 의존).

## 예외 (상세)

분류(Warn=4xx, Error=5xx, `ErrorCode` enum)는 AGENTS.md 참조. 추가 세부:
- stacktrace: `WarnException` 없음(WARN) / `ErrorException` 포함(ERROR).
- `ErrorCode` 필드: 상태코드 + 코드 + 메시지.
- 실제 응답 변환은 `api`의 `GlobalExceptionHandler`(`@ResponseStatus(HttpStatus.OK)` — HTTP 항상 200, 에러는 body).

## 직렬화

- `ObjectMapperFactory.create()`로 공통 `ObjectMapper`를 만든다. `api`의 `JacksonConfig`가 Spring Bean으로 등록.
- 날짜 포맷: `LocalDate` → `yyyy-MM-dd`, `LocalDateTime` → `yyyy-MM-dd HH:mm:ss`.

## 로깅

- `@Loggable`: 클래스/메서드에 붙이면 AOP로 메서드명·파라미터·반환값·실행시간·예외를 자동 로깅(AOP 적용은 `api`의 `LoggingAspect`).
