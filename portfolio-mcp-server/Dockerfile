# ── Stage 1: Build ──
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Cache dependencies separately (only re-downloads when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src/ ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ──
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/*.jar app.jar

# No EXPOSE: this is a stdio MCP server. The client spawns it with `docker run -i`
# and speaks JSON-RPC over stdin/stdout — it listens on no port and must not be
# published on one.
#
# stdout carries the protocol, so the JVM must stay silent on it. The app disables
# the banner and logs to stderr (logback-spring.xml); keep any flag added here off
# stdout too.
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
