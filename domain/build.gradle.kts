plugins {
    id("spring-convention")
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

dependencies {
    implementation(project(":common"))

    api(Dependencies.SPRING_BOOT_STARTER_DATA_JPA)
    runtimeOnly(Dependencies.MYSQL_CONNECTOR)
    testRuntimeOnly(Dependencies.H2)
}
