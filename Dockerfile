# ==========================================================
# venvify-core — production image (multi-stage)
# Build:  docker build -t <user>/venvify-core .
# Tests KHÔNG chạy ở đây — CI/CD chạy `mvnw verify` với MySQL service trước khi build image.
# ==========================================================

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependency theo layer: pom đổi mới phải tải lại, đổi src thì không.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine

# Chạy non-root (production hygiene).
RUN addgroup -S venvify && adduser -S venvify -G venvify
USER venvify

WORKDIR /app
COPY --from=build /build/target/venvify-core-*.jar app.jar

EXPOSE 8080

# MaxRAMPercentage để JVM tôn trọng mem_limit của container thay vì RAM host.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
