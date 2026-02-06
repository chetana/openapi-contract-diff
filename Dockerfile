# Stage 1: Build with Maven
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app

# Installation de Node.js (requis par Vaadin pour le build de production)
RUN apt-get update && apt-get install -y curl && \
    curl -sL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y nodejs

COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
# Build avec le profil production
RUN mvn clean package -Pproduction -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/openapi-contract-diff-1.0-SNAPSHOT.jar app.jar

# Cloud Run attend le port 8080 par défaut
EXPOSE 8080

# Activation explicite du mode production au runtime
ENTRYPOINT ["java", "-Dserver.port=8080", "-Dvaadin.productionMode=true", "-jar", "app.jar"]
