# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ptc && adduser -S -G ptc ptc \
    && mkdir -p /var/lib/ptc/keys && chown -R ptc:ptc /var/lib/ptc
WORKDIR /app
COPY --from=build /src/target/ptc-tracker.jar app.jar
USER ptc
# Signing keys and the data key live on a mounted volume, never in the image.
VOLUME ["/var/lib/ptc/keys"]
ENV PTC_KEYS_DIR=/var/lib/ptc/keys \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
