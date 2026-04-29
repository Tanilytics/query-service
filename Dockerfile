# Multi-stage Dockerfile for query-service
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Maven wrapper and pom first (layer caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:resolve -B -q

# Build
COPY src/ src/
RUN ./mvnw package -DskipTests -B -q

# ---- Runtime image ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root user (Debian uses adduser/groupadd differently than Alpine)
RUN groupadd -r tanalytics && useradd -r -g tanalytics tanalytics
USER tanalytics

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
