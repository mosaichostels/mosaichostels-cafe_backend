Read when: adding/running tests or judging whether a change is covered.
Verified: 2026-10-29 @ 9e39a6f. `JAVA_HOME=<temurin-17> mvn -B -o test`: 156 tests, 0 failures, BUILD SUCCESS.
Covers: src/test/java/**/*.java, pom.xml, .github/workflows/**

# Testing

- Run: `mvn -B verify` (CI gate before deploy). Single class: `mvn -q test -Dtest=<Class>` (not run).
- Size: 33 test classes, 157 `@Test` annotations (156 run) under `src/test/java/com/hostel/ordering/`, mirroring packages. No `src/test/resources`.
- Tools: JUnit 5 (all), Mockito with `MockitoExtension` (13 classes, `@Mock`/`@InjectMocks`), `ReflectionTestUtils` (5), `MockMvc` (1 class), `@TestPropertySource` (1). No embedded Mongo / Testcontainers imports found.

| Area | Test classes |
| --- | --- |
| config | ProdCorsConfigTest, RateLimitFilterTest, RoleHierarchyMethodSecurityTest, SecurityConfigTest, WebConfigTest |
| controller | AuthControllerThrottleTest, HealthAndCorsTest, NotificationControllerSecurityTest, OrderControllerDeleteTest, OrderControllerTest, SuperAdminGateTest |
| dto / model | CreateOrderRequestValidationTest, OrderTest |
| ezee | EzeeChargePostServiceTest, EzeeClientTest, EzeeXmlUtilTest |
| repository | OrderRepositoryClaimTest |
| security | AuthTokenFilterTest, JwtUtilsRevocationTest, LoginThrottleTest |
| service | AuditServiceTest, AuthServiceRevocationTest, CategoryDeleteGuardTest, CategoryRenameCascadeTest, ConfirmTokenServiceTest, DormitoryOrderingTest, OrderCreateIdempotencyTest, OrderServiceTest, OrderStatusSeedingTest, RestoreDeletedItemTest, StaleChargePostRecoveryTest, UserServiceDeleteGuardTest, UserServiceRulesTest |

- eZee/charge-post edits: run at least EzeeChargePostServiceTest, StaleChargePostRecoveryTest, OrderRepositoryClaimTest.
- Coverage tooling (jacoco etc.): none in pom.
