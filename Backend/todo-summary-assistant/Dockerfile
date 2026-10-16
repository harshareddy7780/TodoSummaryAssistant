
# Stage 1: Build the Spring Boot application
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S -D -H -u 10001 -G appgroup appuser

COPY --from=build /app/target/*.jar app.jar

USER appuser

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["wget", "-q", "-O", "/dev/null", "http://localhost:8081/actuator/health"]

ENTRYPOINT ["java", "-jar", "app.jar"]
