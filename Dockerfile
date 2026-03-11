# ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

# Download dependencies first (cached layer)
RUN apk add --no-cache maven && \
    mvn dependency:go-offline --batch-mode -q

# Build the JAR
RUN mvn package -DskipTests --batch-mode -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/travelapi-*.jar app.jar

# Cloud Run expects PORT env variable
ENV PORT=8080
EXPOSE 8080

USER appuser

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
