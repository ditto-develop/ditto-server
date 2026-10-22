plugins {
    id("restdocs-convention")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":infrastructure"))

    implementation(Dependencies.SPRING_BOOT_STARTER_WEB)
    implementation(Dependencies.SPRING_BOOT_STARTER_WEBSOCKET)
    implementation(Dependencies.SPRING_BOOT_STARTER_VALIDATION)
    implementation(Dependencies.SPRING_BOOT_STARTER_SECURITY)
    implementation(Dependencies.SPRING_BOOT_STARTER_AOP)
    implementation(Dependencies.SPRING_BOOT_STARTER_ACTUATOR)
    implementation(Dependencies.SPRING_BOOT_STARTER_THYMELEAF)
    implementation(Dependencies.THYMELEAF_EXTRAS_SPRING_SECURITY)
    implementation(Dependencies.LOGSTASH_LOGBACK)
    implementation(Dependencies.SPRINGDOC_OPENAPI)
    implementation(Dependencies.JJWT_API)

    runtimeOnly(Dependencies.MICROMETER_PROMETHEUS)
    runtimeOnly(Dependencies.MYSQL_CONNECTOR)
    runtimeOnly(Dependencies.JJWT_IMPL)
    runtimeOnly(Dependencies.JJWT_JACKSON)
    runtimeOnly(Dependencies.H2)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(Dependencies.KOTEST_SPRING)
    testImplementation("org.springframework.security:spring-security-test")
}

// 어드민 화면 JS 의 순수 계산을 Node 내장 러너로 검증한다. 패키지 의존성은 없다.
val jsTest by tasks.registering(Exec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    val testFiles = fileTree("src/test/js") { include("**/*.test.js") }
    inputs.files(testFiles, fileTree("src/main/resources/static/admin/js"))
    // 파일 인자가 없으면 node 가 작업 폴더 전체에서 테스트를 찾는다.
    onlyIf { !testFiles.isEmpty }
    executable = "node"
    args("--test")
    argumentProviders.add(CommandLineArgumentProvider { testFiles.files.map { it.absolutePath }.sorted() })
}

tasks.named("check") {
    dependsOn(jsTest)
}
