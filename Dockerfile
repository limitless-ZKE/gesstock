# 1. L'interface (React) est construite à part, puis embarquée dans le programme : une seule adresse pour tous les postes.
FROM node:24-alpine AS interface

WORKDIR /interface

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# 2. Le programme, avec l'interface dans static/ (profil Maven « interface »).
FROM maven:3.9.11-eclipse-temurin-17-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
COPY --from=interface /interface/dist frontend/dist
RUN ./mvnw -B -DskipTests -Pinterface package

FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /workspace/target/gesstock-*.jar app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
