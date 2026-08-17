FROM maven:3.9.8-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY . .

# Build every supported runtime module before MODULE enters the build graph. BuildKit can
# therefore reuse this reactor layer for every backend image in one Compose build.
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -q -DskipTests \
      -pl :community-app,:community-gateway,:community-im-gateway,:community-oss,:im-core,:im-realtime \
      -am package

ARG MODULE

RUN set -eu; \
    if [ -z "${MODULE:-}" ]; then \
      echo "[docker-build] ERROR: build-arg MODULE is required (Maven artifactId, e.g. community-app)."; \
      exit 1; \
    fi; \
    jar_path="$( \
      find . -maxdepth 4 -type f -path "*/${MODULE}/target/*.jar" \
        ! -name "original-*.jar" \
        ! -name "*-sources.jar" \
        ! -name "*-javadoc.jar" \
        ! -name "*-tests.jar" \
        -print | sort | head -n 1 \
    )"; \
    if [ -z "${jar_path}" ]; then \
      echo "[docker-build] ERROR: Cannot find built jar for MODULE='${MODULE}'." >&2; \
      echo "[docker-build] Expected: */${MODULE}/target/*.jar (or ${MODULE}/target/*.jar) (excluding original-*, *-sources.jar, *-javadoc.jar, *-tests.jar)." >&2; \
      echo "[docker-build] Existing jar(s) under */target/*.jar and */*/target/*.jar:" >&2; \
      find . -maxdepth 4 -type f -path "*/target/*.jar" -print | sed "s|^\\./||" >&2 || true; \
      exit 1; \
    fi; \
    echo "[docker-build] Using jar: ${jar_path}"; \
    cp "${jar_path}" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy

ARG OTEL_JAVA_AGENT_VERSION=2.23.0

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && groupadd --gid 10001 community \
    && useradd --uid 10001 --gid community --no-create-home --home-dir /tmp/community-runtime/home --shell /usr/sbin/nologin community \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN set -eu; \
    agent_base_url="https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent-${OTEL_JAVA_AGENT_VERSION}.jar"; \
    mkdir -p /otel /opt/community; \
    curl -fsSL "${agent_base_url}" -o /otel/opentelemetry-javaagent.jar; \
    curl -fsSL "${agent_base_url}.sha256" -o /tmp/opentelemetry-javaagent.jar.sha256; \
    printf '%s  %s\n' "$(cat /tmp/opentelemetry-javaagent.jar.sha256)" "/otel/opentelemetry-javaagent.jar" | sha256sum -c -; \
    rm -f /tmp/opentelemetry-javaagent.jar.sha256
COPY --from=build /workspace/app.jar /app/app.jar
COPY scripts/run-backend-service.sh /opt/community/run-backend-service.sh
RUN chmod 0755 /opt/community/run-backend-service.sh

ENV HOME=/tmp/community-runtime/home \
    JAVA_OPTS=""

USER 10001:10001

HEALTHCHECK --interval=10s --timeout=3s --retries=30 \
  CMD curl -fsS "http://localhost:${SERVER_PORT:-8080}/actuator/health" >/dev/null || exit 1

ENTRYPOINT ["/opt/community/run-backend-service.sh"]
