plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
    implementation("org.jetbrains.kotlin:kotlin-allopen:1.9.25")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.11")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:6.0.1.5171")
}
