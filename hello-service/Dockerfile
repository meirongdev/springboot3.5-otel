FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY shared/ shared/
COPY arch-tests/ arch-tests/
COPY hello-service/ hello-service/
COPY user-service/ user-service/
COPY greeting-service/ greeting-service/
RUN ./gradlew :hello-service:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S app && adduser -S app -G app

# Create logs directory for GC logs and heap dumps
RUN mkdir -p /logs && chown -R app:app /logs

# Copy application
COPY --from=builder /build/hello-service/build/libs/*.jar app.jar

USER app

EXPOSE 8080

# Use JDK_JAVA_OPTIONS for configuration (set via Docker Compose environment)
# Default JFR profiling enabled for production observability
ENTRYPOINT ["java", "-jar", "app.jar"]
