plugins {
    id("spring-convention")
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation("org.springframework:spring-web")
    implementation(Dependencies.AWS_S3)
    implementation(Dependencies.FIREBASE_ADMIN)
    // 애플 ID 토큰(JWT) 서명 검증용
    implementation(Dependencies.JJWT_API)
    runtimeOnly(Dependencies.JJWT_IMPL)
    runtimeOnly(Dependencies.JJWT_JACKSON)
}
