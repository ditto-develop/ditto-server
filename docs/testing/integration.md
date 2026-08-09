# 테스트 — 통합/단위

> 통합·단위 테스트를 작성/수정하기 전에 아래 **필수 규칙**을 먼저 적용하라. 한 줄로 보여도 건너뛰지 말 것 — 그 아래는 적용 방법·예시다.

## ⚠️ 필수 규칙 (먼저 적용)

- 통합 테스트는 `IntegrationTest`를 상속한다 — `@SpringBootTest`·`FreeSpec`을 **직접** 붙이거나 상속하지 마라.
- 모킹은 MockK만 — `@MockBean`/`@SpyBean` 금지(스프링 컨텍스트 오염).
- JUnit5는 메서드명 영어 + `@DisplayName`(한글) — **백틱 한글 메서드명 금지.**
- 비즈니스 동작은 통합 테스트로, 단위 테스트는 순수 로직·util에만.
- 엔티티는 `test-fixtures` 팩토리로 생성(직접 생성 금지).
- 테스트를 통과시키려고 단언을 약화하지 마라.

머지 게이트(Jacoco 50% + Sonar New Code 80%)는 AGENTS.md 참조.

---
아래는 위 규칙의 **방법·예시**다.

## 프레임워크

Kotest FreeSpec(통합, 한글 테스트명) / JUnit5(문서화 테스트 → `docs/testing/rest-docs.md`) / MockK.

## 네이밍 예시

Kotest FreeSpec — 한글 문자열:
```kotlin
"유저 생성" - {
    "정상적인 요청이면 유저가 생성된다" {
        // ...
    }
}
```

JUnit5 — 메서드명 영어 + `@DisplayName`:
```kotlin
@Test
@DisplayName("정상적인 요청이면 유저가 생성된다")
fun createUser() { /* ... */ }
```

## `IntegrationTest`가 자동으로 해주는 것

- `@SpringBootTest`, `SpringExtension`(Kotest+Spring 연동), `DatabaseCleanExtension`(매 테스트 전 H2 전체 truncate).
- 위치: `api/src/test/kotlin/com/ditto/api/support/IntegrationTest.kt`

작성 예:
```kotlin
class UserServiceTest(
    private val userService: UserService,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {
    "유저 생성" - {
        "정상적인 요청이면 유저가 생성된다" {
            val result = userService.create(UserFixture.createRequest())
            result.name shouldBe "홍길동"
        }
    }
})
```

## 커버리지 (세부)

- 제외 대상(`sonar-convention`): `config/**`, `*Application*`, `*Config*`, `*Request*`, `*Response*`, `*Dto*`.
- CI: `./gradlew build check`(Jacoco) → `./gradlew sonarqube`(SonarCloud). ⚠️ `build check`가 통과해도 Sonar New Code 게이트는 실패할 수 있으니 PR 체크를 함께 확인.
- **Jacoco 는 모듈별로 자기 클래스만 집계한다.** `domain`·`common` 코드를 `api` 테스트로만 검증하면 그 모듈 리포트에 커버로 잡히지 않아, `build check`는 통과하고 Sonar New Code 만 실패한다 — QueryDSL 조회·값 객체처럼 다른 모듈에 넣은 코드는 **그 모듈에 테스트를 함께 둔다.** 실패 시 파일별 미커버 라인부터 확인: `curl -s "https://sonarcloud.io/api/measures/component_tree?component=ditto-develop_ditto-server&pullRequest=<번호>&metricKeys=new_uncovered_lines,new_lines_to_cover&qualifiers=FIL&ps=100"`.
- **`inline` 함수는 커버리지를 덮을 수 없다.** 호출부로 인라인돼 선언 파일에 실행될 바이트코드가 남지 않아 영구 미커버로 집계된다. 작은 헬퍼에 습관적으로 붙이지 말 것.
- **테스트가 없는 모듈은 게이트가 잠들어 있다.** `test` 태스크가 NO-SOURCE 면 검증도 스킵되므로, 그 모듈에 첫 테스트를 넣는 순간 기존 클래스 전체가 50% 기준에 걸린다(`common`이 그 상태다). 첫 테스트 추가는 별도 작업으로 다룬다.
