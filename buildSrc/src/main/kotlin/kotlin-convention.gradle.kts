import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    jacoco
}

group = "com.ditto"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xjvm-default=all",
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude("**/config/**", "**/dto/**", "**/constants/**", "**/entity/**", "**/repository/**", "**/BaseEntity*", "**/KakaoOAuthClient*")
            }
        },
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude("**/config/**", "**/dto/**", "**/constants/**", "**/entity/**", "**/repository/**", "**/BaseEntity*", "**/KakaoOAuthClient*")
            }
        },
    )
    violationRules {
        rule {
            limit {
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

dependencies {
    implementation(Dependencies.KOTLIN_REFLECT)
    implementation(Dependencies.KOTLIN_STDLIB)
    implementation(Dependencies.KOTLIN_LOGGING)
    testImplementation(Dependencies.KOTEST_RUNNER)
    testImplementation(Dependencies.KOTEST_ASSERTIONS)
    testImplementation(Dependencies.KOTEST_PROPERTY)
    testImplementation(Dependencies.MOCKK)
}
