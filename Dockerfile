# Builds and runs Friends League in two stages:
# 1. A full JDK builds the jar (tests run in GitHub Actions, so they are skipped here).
# 2. A small JRE image runs it.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# The Maven wrapper and pom.xml first: as long as they don't change,
# Docker reuses the downloaded dependencies and the next builds are much faster.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw --batch-mode dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

# Never run as root inside the container.
RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

# Render's free plan has 512 MB of memory: let Java use most of it, but not all.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
