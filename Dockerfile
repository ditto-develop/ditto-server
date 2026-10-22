FROM eclipse-temurin:21-jdk-alpine AS builder

# :api:test 가 check 를 거쳐 jsTest(node --test)까지 돌린다.
RUN apk add --no-cache nodejs

WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN ./gradlew :api:test --no-daemon --parallel
RUN ./gradlew :api:bootJar --no-daemon --parallel

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S ditto && adduser -S ditto -G ditto

COPY --from=builder /app/api/build/libs/*.jar app.jar

RUN chown -R ditto:ditto /app
USER ditto

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
