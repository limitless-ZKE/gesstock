FROM maven:3.9.11-eclipse-temurin-17-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /workspace/target/gesstock-*.jar app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
