# jar 는 CD 러너가 Gradle 캐시로 미리 만든다(`./gradlew :api:bootJar`). 테스트는 PR CI 에서 이미 통과했으므로
# 이미지 빌드 안에서 다시 돌리지 않는다. 로컬에서 이미지를 만들 때도 bootJar 를 먼저 실행할 것. 배경: ADR 0050.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S ditto && adduser -S ditto -G ditto

COPY api/build/libs/*.jar app.jar

RUN chown -R ditto:ditto /app
USER ditto

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
