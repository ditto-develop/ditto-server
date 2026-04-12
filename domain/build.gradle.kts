plugins {
    id("spring-convention")
    kotlin("kapt")
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

dependencies {
    implementation(project(":common"))

    api(Dependencies.SPRING_BOOT_STARTER_DATA_JPA)
    implementation(Dependencies.QUERYDSL_JPA)
    kapt(Dependencies.QUERYDSL_APT)
    runtimeOnly(Dependencies.MYSQL_CONNECTOR)
    testRuntimeOnly(Dependencies.H2)
}
