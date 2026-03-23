plugins {
    id("kotlin-convention")
    id("io.spring.dependency-management")

    kotlin("plugin.jpa")

    `java-library`
    `java-test-fixtures`
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${DependencyVersions.SPRING_BOOT}")
    }
}

dependencies {
    implementation(project(":common"))

    api(Dependencies.SPRING_BOOT_STARTER_DATA_JPA)
}
