# Multi-stage Dockerfile for Elysium Server (Modular Monolith)
FROM gradle:8.10-jdk21 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd -r elysium && useradd -r -g elysium -d /app -s /sbin/nologin elysium
COPY --from=build /home/gradle/src/build/libs/elysium-server-1.0.0.jar /app/elysium-server.jar

USER elysium
EXPOSE 8080 8081

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/health/live || exit 1

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/elysium-server.jar"]
