# Multi-stage build: compile in a JDK image, run in a slim JRE image.
# Keeps the final image smaller and avoids shipping the Gradle build tooling
# itself into the runtime container.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Gradle wrapper and build files first - Docker layer caching means
# dependency resolution is only re-run when these actually change, not on
# every source code edit.
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN ./gradlew dependencies --no-daemon || true

# Now copy actual source and build
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user - standard production hardening practice, not
# strictly required for a local demo, but worth doing correctly since this
# is a real artifact for the portfolio, not throwaway code.
RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
