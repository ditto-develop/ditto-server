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
    runtimeOnly(Dependencies.MICROMETER_PROMETHEUS)
    implementation(Dependencies.LOGSTASH_LOGBACK)
    implementation(Dependencies.SPRINGDOC_OPENAPI)
    testImplementation(Dependencies.KOTEST_SPRING)
    testRuntimeOnly(Dependencies.H2)
}
