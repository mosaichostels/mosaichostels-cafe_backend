# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# JVM flags tuned for Railway's low-memory containers:
#   -XX:+UseContainerSupport         - respect container cgroup memory limits
#   -XX:MaxRAMPercentage=65.0        - cap heap at 65% of container RAM (leaves room for metaspace + threads)
#   -XX:InitialRAMPercentage=30.0    - start with a smaller heap so startup doesn't OOM
#   -XX:+UseSerialGC                 - Serial GC uses far less overhead than G1 on small containers
#   -XX:CICompilerCount=2            - minimum 2 JIT compiler threads required for tiered compilation
#   -Xss256k                         - smaller thread stack (default 512k) — saves ~256k per thread
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=65.0", \
  "-XX:InitialRAMPercentage=30.0", \
  "-XX:+UseSerialGC", \
  "-XX:CICompilerCount=2", \
  "-Xss256k", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]