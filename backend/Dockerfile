# Multi-stage build for the Spring Boot backend.
#
# Build:  docker build -t canton-backend .
# Run:    docker run -p 8080:8080 canton-backend
#
# On Render (or similar PaaS), set the service's root directory to
# `backend/` and it will be picked up automatically as a Docker service.
# Render injects a PORT env var; application.yml binds to ${PORT:8080}.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies separately from source changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
