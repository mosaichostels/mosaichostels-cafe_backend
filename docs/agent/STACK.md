Read when: choosing libraries, checking versions, or touching the build/image.
Verified: 2026-10-29 @ 8b57217 (source: pom.xml, Dockerfile)
Covers: pom.xml, Dockerfile, gradle.properties

# Stack

| Item | Value |
| --- | --- |
| Language / build | Java 17 (pom.xml:16), Maven; no wrapper (`mvnw` absent) |
| Framework | Spring Boot 3.5.10 parent (pom.xml:7) |
| Starters | web, data-mongodb, security, validation, test |
| JWT | jjwt 0.11.5 (api/impl/jackson) |
| Push | firebase-admin 9.1.1 |
| Boilerplate | Lombok (annotation processor in maven-compiler-plugin 3.11.0) |
| Dev | spring-boot-devtools (excluded from the jar) |
| Tests | spring-boot-starter-test: JUnit 5, Mockito (see TESTING.md) |
| DB | MongoDB via Spring Data (`MongoTemplate` for custom queries) |
| Image build | `maven:3.9.6-eclipse-temurin-17` (stage 1) |
| Image run | `eclipse-temurin:17-jre`, `-Xmx4g`, `-Dserver.port=7860`, `-Dspring.profiles.active=prod` |
| Container health | `HEALTHCHECK` curls `localhost:7860/health` every 2m (Dockerfile:17) |
| Actuator | only `health` exposed, details hidden (application-prod.yml:35-40) |
| Prod tuning | Tomcat threads 200/min-spare 20, `lazy-initialization: true` (application-prod.yml:2-12) |
