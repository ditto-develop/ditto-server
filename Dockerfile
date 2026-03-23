FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN ./gradlew :api:bootJar --no-daemon --parallel

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S ditto && adduser -S ditto -G ditto

COPY --from=builder /app/api/build/libs/*.jar app.jar

RUN chown -R ditto:ditto /app
USER ditto

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
