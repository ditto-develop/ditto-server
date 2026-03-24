plugins {
    id("spring-convention")
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation("org.springframework:spring-web")

    testImplementation(Dependencies.KOTEST_SPRING)
}
