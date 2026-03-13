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
        property("sonar.coverage.exclusions",
            "**/config/**,**/*Application*,**/*Config*,**/*Request*,**/*Response*,**/*Dto*"
        )
    }
}
