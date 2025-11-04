####
# This Dockerfile builds the Keycloak Kafka Sync Agent using a multi-stage build
# for optimal image size and security.
####

## Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Copy Maven wrapper and pom.xml first for better layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer if pom.xml unchanged)
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw package -DskipTests

## Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install curl for health checks (minimal overhead)
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 quarkus && \
    adduser -u 1001 -G quarkus -s /bin/sh -D quarkus

# Copy the built application from build stage
COPY --from=build --chown=quarkus:quarkus /build/target/quarkus-app/ ./

# Create directory for SQLite database
RUN mkdir -p /app/data && chown quarkus:quarkus /app/data

# Switch to non-root user
USER quarkus

# Expose application port
EXPOSE 57010

# Health check configuration
# Uses the Quarkus health endpoint to determine container health
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:57010/health/ready || exit 1

# Set environment variables for container runtime
ENV QUARKUS_HTTP_PORT=57010 \
    SQLITE_DB_PATH=/app/data/sync-agent.db

# Run the application
CMD ["java", "-jar", "quarkus-run.jar"]
