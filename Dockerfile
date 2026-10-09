# syntax=docker/dockerfile:1

# ---- Build stage ----------------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Resolve dependencies first so they are cached between source changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/orderflow-service-*.jar extract --layers --launcher --destination target/layers

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

# Copy Spring Boot layers from least to most frequently changing
COPY --from=build /workspace/target/layers/dependencies/ ./
COPY --from=build /workspace/target/layers/spring-boot-loader/ ./
COPY --from=build /workspace/target/layers/snapshot-dependencies/ ./
COPY --from=build /workspace/target/layers/application/ ./

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
