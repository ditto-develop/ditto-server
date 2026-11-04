plugins {
    id("org.sonarqube")
}

sonarqube {
    properties {
        property("sonar.projectKey", "ditto-develop_ditto-server")
        property("sonar.organization", "ditto-develop")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sources", "src/main/kotlin")
        property("sonar.tests", "src/test/kotlin")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.kotlin.source.version", DependencyVersions.KOTLIN)
        property("sonar.coverage.jacoco.xmlReportPaths",
            "${rootProject.layout.buildDirectory.get()}/reports/jacoco/jacocoRootReport/jacocoRootReport.xml"
        )
        property("sonar.exclusions",
            "**/generated/**,**/config/**,**/*Application*,**/*Config*"
        )
        // ShedLock: 스키마 선언용 엔티티(행은 ShedLock 이 JDBC 로 다룸)라 생성자가 호출될 일이 없다 (ADR 0049)
        property("sonar.coverage.exclusions",
            "**/config/**,**/*Application*,**/*Config*,**/*Request*,**/*Response*,**/*Dto*," +
                "**/system/entity/ShedLock.kt"
        )
    }
}
