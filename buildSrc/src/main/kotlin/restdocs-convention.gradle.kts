plugins {
    id("spring-convention")
    id("com.epages.restdocs-api-spec")
}

dependencies {
    testImplementation(Dependencies.SPRING_RESTDOCS_MOCKMVC)
    testImplementation(Dependencies.RESTDOCS_API_SPEC_MOCKMVC)
}

openapi3 {
    setServer("https://api.ditto.pics")
    title = "Ditto API"
    description = "Ditto 서비스 API 문서"
    version = "v1"
    format = "yaml"
    outputDirectory = "src/main/resources/static/docs"
    outputFileNamePrefix = "openapi"
}

tasks.named("test") {
    finalizedBy("openapi3")
}
