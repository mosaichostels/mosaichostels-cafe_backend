# Stack

Verified from pom.xml and Dockerfile.
- Java 17 (`release` 17), Maven, Spring Boot 3.5.10
- Starters: web, data-mongodb, security, validation, test; devtools excluded from the jar
- jjwt 0.11.5 (JWT), Lombok 1.18.42, firebase-admin 9.1.1 (FCM)
- Docker: build `maven:3.9.6-eclipse-temurin-17`, run `eclipse-temurin:17-jre`, `-Xmx4g`, profile `prod`
- Test libs: spring-boot-starter-test only (specific frameworks used beyond that: see TESTING.md)
