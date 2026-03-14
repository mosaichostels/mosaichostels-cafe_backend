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
COPY --from=builder /app/target/ordering-system-1.0.0.jar app.jar

EXPOSE 8080

# JVM flags tuned for Railway's low-memory containers (512MB–1GB):
#
#   UseContainerSupport      — respect cgroup memory limits (essential in containers)
#   MaxRAMPercentage=60.0    — heap capped at 60% of container RAM
#                              (was 75% — left too little room for metaspace + threads + Firebase native memory)
#   InitialRAMPercentage=25.0— start with a small heap; Railway kills apps that spike at startup
#   MaxMetaspaceSize=128m    — WITHOUT this, metaspace grows unboundedly and causes OOM
#                              Spring Boot 3 + Firebase SDK loads ~80–100MB of classes
#   UseSerialGC              — Serial GC uses ~10MB overhead vs G1GC's ~100MB+ overhead
#                              on containers <2GB Serial GC is the right choice
#   CICompilerCount=2        — minimum required for tiered compilation; don't go lower
#   Xss256k                  — smaller thread stacks (default 512k); with 20 Tomcat threads
#                              this saves ~5MB
#   TieredStopAtLevel=1      — skip C2 JIT on startup (interpreted + C1 only);
#                              reduces startup-time CPU/memory burst that causes OOM
#   java.security.egd        — faster random number seeding (avoids /dev/random blocking)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=60.0", \
  "-XX:InitialRAMPercentage=25.0", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:+UseSerialGC", \
  "-XX:CICompilerCount=2", \
  "-XX:TieredStopAtLevel=1", \
  "-Xss256k", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]
