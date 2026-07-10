# infrastructure 모듈

외부 인프라 연동(Redis, 외부 API 등) 담당. Spring 모듈(`spring-convention`), `bootJar` 비활성.

## 담는 것

- 외부 인프라 연동 코드와 설정 클래스
- 인프라별 하위 패키지로 구분 (예: `redis/` 아래 `RedisConfig`)

## 설정 파일

- 각 인프라 설정은 해당 모듈 resources에 `application-{name}.yml`로 관리 (예: `application-redis.yml`).
- 설정 클래스는 `api` 모듈에서 `@Import`로 명시적으로 가져간다 (예: `@Import(RedisConfig::class)`).
