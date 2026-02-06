# Stage 1: Build with Maven
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/openapi-contract-diff-1.0-SNAPSHOT.jar app.jar

# Cloud Run attend le port 8080 par défaut
EXPOSE 8080

ENTRYPOINT ["java", "-Dserver.port=8080", "-jar", "app.jar"]
