FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml lombok.config ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="ms-vehiculos" \
      org.opencontainers.image.version="${APP_VERSION}"

RUN apk upgrade --no-cache \
 && addgroup -S -g 1001 app \
 && adduser -S -u 1001 -G app app

WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar

USER 1001
EXPOSE 9004

ENV APP_VERSION=${APP_VERSION} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=20s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:9004/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
