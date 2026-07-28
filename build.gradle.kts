plugins {
    id("sonar-convention")
    jacoco
}

allprojects {
    repositories {
        mavenCentral()
    }
}

// Sonar가 읽는 커버리지 소스(sonar-convention의 xmlReportPaths)를 실제로 생성한다.
// 모듈별 리포트는 자기 모듈 클래스만 담아, 모듈 경계를 넘는 커버리지(예: api 통합 테스트가
// 실행한 domain 클래스)가 누락된다 — 전 모듈 실행 데이터·클래스를 단일 XML로 집계해 해결.
tasks.register<JacocoReport>("jacocoRootReport") {
    group = "verification"
    description = "전 모듈 Jacoco 커버리지를 단일 XML로 집계한다 (SonarCloud 입력)"
    dependsOn(subprojects.map { "${it.path}:test" })
    executionData(fileTree(rootDir) { include("*/build/jacoco/test.exec") })
    sourceDirectories.from(files(subprojects.map { it.file("src/main/kotlin") }))
    classDirectories.from(files(subprojects.map { it.fileTree("build/classes/kotlin/main") }))
    reports {
        xml.required.set(true)
        html.required.set(false)
    }
}

// CI의 `./gradlew sonarqube`가 집계 리포트를 먼저 만들도록 보장한다.
tasks.matching { it.name == "sonar" || it.name == "sonarqube" }.configureEach {
    dependsOn("jacocoRootReport")
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

        // 중첩 git 워크트리(.claude/worktrees/**)는 저장소의 완전한 체크아웃이라 같은 테스트 파일이 다시 걸린다.
        // 허용 목록이 루트 기준 상대 경로라 워크트리 안 경로는 매칭되지 않아 베이스 클래스가 전부 오탐이 된다.
        // 판정은 반드시 rootDir 기준 상대 경로로 한다 — 절대 경로로 거르면 워크트리 '안에서' 빌드할 때
        // rootDir 자체가 .claude 를 포함해 그 안의 모든 파일이 검사에서 빠진다(검사기가 조용히 무력화됨).
        val excludedDirs = listOf("build", ".claude")

        rootDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "/src/test/" in it.path }
            .map { it to it.relativeTo(rootDir).path.replace('\\', '/') }
            .filterNot { (_, rel) -> excludedDirs.any { rel.startsWith("$it/") || "/$it/" in rel } }
            .forEach { (file, rel) ->
                val text = file.readText()
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
