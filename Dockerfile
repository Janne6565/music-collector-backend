FROM maven:3.9-eclipse-temurin-25 AS build
# The tag being published, so the running service can name itself. Defaults to "dev" for
# a plain `docker build` with no argument.
ARG MC_RELEASE=dev
WORKDIR /build
# Warm the dependency layer before the sources land, so code changes don't re-resolve.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests -Dmc.release="${MC_RELEASE}"

FROM eclipse-temurin:25-jre
WORKDIR /app
# Traces only. Metrics come from Micrometer's own OTLP registry, which is already
# publishing http.server.requests and the collection gauges under the same service name --
# letting the agent export metrics too would give SigNoz two of everything under two
# different naming schemes. Pinned rather than :latest so a rebuild of an old tag produces
# the same image.
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.20.1/opentelemetry-javaagent.jar /app/otel-agent.jar
RUN chmod 0644 /app/otel-agent.jar
COPY --from=build /build/target/backend-*.jar app.jar
EXPOSE 8080
# The agent is inert with no OTEL_EXPORTER_OTLP_ENDPOINT set, so a plain `docker run` and
# the local compose stack behave exactly as they did before.
#
# -Dotel.metrics.exporter=none has to be a system property, not the OTEL_METRICS_EXPORTER
# environment variable that would otherwise say the same thing. The agent reads both; Boot's
# OpenTelemetry module reads only the environment variable, and takes "none" to mean its own
# Micrometer registry as well. Setting it there turns off the exporter that carries the
# application's metrics and leaves the agent's duplicate JVM metrics as the only ones -- the
# exact opposite of what is wanted, and silent.
ENTRYPOINT ["java", "-javaagent:/app/otel-agent.jar", "-Dotel.metrics.exporter=none", "-jar", "/app/app.jar"]
