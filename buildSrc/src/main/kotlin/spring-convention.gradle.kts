plugins {
    id("kotlin-convention")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

tasks.named<Jar>("jar") {
    enabled = false
}

dependencies {
    implementation(Dependencies.SPRING_BOOT_STARTER)
    implementation(Dependencies.JACKSON_MODULE_KOTLIN)
    testImplementation(Dependencies.SPRING_BOOT_STARTER_TEST)
    testImplementation(Dependencies.SPRING_MOCKK)
}
