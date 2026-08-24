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
COPY --from=build /build/target/backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
