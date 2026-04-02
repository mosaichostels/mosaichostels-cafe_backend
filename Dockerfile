# Stage 1: Build
FROM --platform=$BUILDPLATFORM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# Hugging Face requires port 7860
EXPOSE 7860

# Healthy check endpoint for 24/7 uptime monitoring and keep-alive
HEALTHCHECK --interval=2m --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:7860/health || exit 1

# Production optimizations for Hugging Face (16GB RAM)
# -Xmx4g: Utilizing a high-performance 4GB of the 16GB available.
# -XX:+UseG1GC: Standard for modern high-RAM environments.
ENTRYPOINT ["java", \
  "-Xmx4g", \
  "-Dserver.port=7860", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]
