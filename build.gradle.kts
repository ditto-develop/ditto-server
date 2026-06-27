plugins {
    id("sonar-convention")
}

allprojects {
    repositories {
        mavenCentral()
    }
}

// 문서로만 강제하던 must-do 중 기계 판정 가능한 것을 빌드에서 확정 강제한다(낡음 방지).
// 위반 시 빌드 실패 + 메시지가 관련 docs를 가리킨다. 배경: docs/adr/0001-agent-doc-hierarchy.md
tasks.register("verifyConventions") {
    group = "verification"
    description = "테스트/마이그레이션 컨벤션 위반을 검사한다 (docs/testing, docs/modules/domain.md)"
    doLast {
        // @SpringBootTest 직접 사용이 허용되는 베이스/지원 클래스 (파일명만 비교하면 동명이인이 통과하므로 경로로 명시)
        val springBootTestAllowed = setOf(
            "api/src/test/kotlin/com/ditto/api/support/IntegrationTest.kt",
            "api/src/test/kotlin/com/ditto/api/support/RestDocsTest.kt",
            "api/src/test/kotlin/com/ditto/api/admin/AdminWebTest.kt",
            "domain/src/test/kotlin/com/ditto/domain/support/IntegrationTest.kt",
        )
        // 주석·문자열 언급 오탐을 피하려고 행 선두 애너테이션 선언만 매칭
        val mockBean = Regex("""^\s*@(MockBean|SpyBean)""", RegexOption.MULTILINE)
        val backtickHangul = Regex("fun\\s+`[^`]*[가-힣][^`]*`")
        val springBootTest = Regex("""^\s*@SpringBootTest""", RegexOption.MULTILINE)
        val violations = mutableListOf<String>()

        rootDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "/src/test/" in it.path && "/build/" !in it.path }
            .forEach { file ->
                val text = file.readText()
                val rel = file.relativeTo(rootDir).path.replace('\\', '/')
                if (mockBean.containsMatchIn(text)) {
                    violations += "$rel — @MockBean/@SpyBean 금지(MockK 사용). docs/testing/integration.md"
                }
                if (backtickHangul.containsMatchIn(text)) {
                    violations += "$rel — 백틱 한글 메서드명 금지(@DisplayName 사용). docs/testing/integration.md"
                }
                if (rel !in springBootTestAllowed && springBootTest.containsMatchIn(text)) {
                    violations += "$rel — @SpringBootTest 직접 사용 금지(IntegrationTest 상속). docs/testing/integration.md"
                }
            }

        file("domain/db").listFiles { _, name -> name.endsWith(".sql") }?.forEach { sql ->
            if (!sql.name.matches(Regex("V\\d{14}_.*"))) {
                violations += "domain/db/${sql.name} — 마이그레이션 파일명 형식 위반(create_sql.sh로 생성). docs/modules/domain.md"
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException("컨벤션 위반 ${violations.size}건:\n" + violations.joinToString("\n") { "  - $it" })
        }
    }
}

// 각 모듈 check가 루트 검사에 의존 → CI의 `./gradlew build check`가 그대로 실행
subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(":verifyConventions")
    }
}
