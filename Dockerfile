# ─── Stage 1: Build frontend ──────────────────────────────────────────────────
FROM node:22-alpine AS frontend-builder

WORKDIR /workspace

# Install dependencies first (cache layer)
COPY frontend/package.json frontend/package-lock.json frontend/
WORKDIR /workspace/frontend
RUN npm ci

# Copy source and build
# vite.config.ts outputs to ../src/main/resources/static
COPY frontend/ .
RUN npm run build

# ─── Stage 2: Build backend JAR ───────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS backend-builder

WORKDIR /app

# Resolve dependencies first (cache layer)
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

# Copy frontend build output before compiling Java
COPY --from=frontend-builder /workspace/src/main/resources/static src/main/resources/static

# Copy source and build fat JAR (skip tests — run in CI separately)
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ─── Stage 3: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=backend-builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
