plugins {
    id("spring-convention")
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":infrastructure"))

    testImplementation(testFixtures(project(":domain")))
    testImplementation(Dependencies.KOTEST_SPRING)
    testRuntimeOnly(Dependencies.H2)
}
