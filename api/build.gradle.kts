plugins {
    id("restdocs-convention")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":infrastructure"))

    implementation(Dependencies.SPRING_BOOT_STARTER_WEB)
    implementation(Dependencies.SPRING_BOOT_STARTER_VALIDATION)
    implementation(Dependencies.SPRING_BOOT_STARTER_SECURITY)
    implementation(Dependencies.SPRING_BOOT_STARTER_AOP)
    implementation(Dependencies.SPRING_BOOT_STARTER_ACTUATOR)
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
}
