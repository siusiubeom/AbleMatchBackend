FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Xmx256m"

COPY . .

RUN ls -R src || (echo "❌ src directory NOT FOUND" && exit 1)

RUN chmod +x gradlew
RUN ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
