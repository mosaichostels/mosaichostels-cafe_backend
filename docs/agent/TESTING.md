Read when: adding/running tests or judging whether a change is covered.
Verified: 2026-11-11 @ 2626fed + final review fixes. `.superpowers/run-tests.sh` (Java 17): 456 tests, 0 failures (sum of `target/surefire-reports/TEST-*.xml`).
Covers: src/test/java/**/*.java, pom.xml, .github/workflows/**

# Testing

- Run: `mvn -B verify` (CI gate before deploy). Single class: `mvn -q test -Dtest=<Class>` (not run).
- Size: 55 test classes, 456 tests run under `src/test/java/com/hostel/ordering/`, mirroring packages. No `src/test/resources`.
- Tools: JUnit 5 (all), Mockito with `MockitoExtension` (13 classes, `@Mock`/`@InjectMocks`), `ReflectionTestUtils` (5), `MockMvc` (1 class), `@TestPropertySource` (1). No embedded Mongo / Testcontainers imports found.

| Area | Test classes |
| --- | --- |
| config | ProdCorsConfigTest, RateLimitFilterTest, RoleHierarchyMethodSecurityTest, SecurityConfigTest, WebConfigTest |
| controller | AppVersionControllerTest, AuthControllerThrottleTest, HealthAndCorsTest, NotificationControllerSecurityTest, OrderControllerDeleteTest, OrderControllerTest, SuperAdminGateTest |
| dto / model | CreateOrderRequestValidationTest, OrderTest |
| ezee | EzeeChargePostServiceTest, EzeeClientTest, EzeeXmlUtilTest |
| pms | EzeeGatewayTest, MongoPmsWriteClaimStoreTest, PmsBookingServiceTest, PmsCacheTest, PmsExceptionMappingTest, PmsFrontDeskControllerTest, PmsFrontDeskServiceTest, PmsGuestServiceTest, PmsHousekeepingServiceTest, PmsPropertiesTest, PmsRateLimiterTest, PmsReconcileServiceTest, PmsStatusControllerTest, PmsUnconfirmedControllerTest, PmsWriteGuardTest; helper `InMemoryPmsWriteClaimStore` |
| repository | OrderRepositoryClaimTest |
| security | RoleMatrixTest (role by route matrix, kitchen/housekeeping/admin/superadmin/staff), AuthTokenFilterTest, JwtUtilsRevocationTest, LoginThrottleTest |
| service | AuditServiceTest, AuthServiceRevocationTest, CategoryDeleteGuardTest, CategoryRenameCascadeTest, ConfirmTokenServiceTest, DormitoryOrderingTest, OrderCreateIdempotencyTest, OrderServiceTest, OrderStatusSeedingTest, RestoreDeletedItemTest, StaleChargePostRecoveryTest, UserServiceDeleteGuardTest, UserServiceRulesTest |

- `InMemoryPmsWriteClaimStore` is a test twin of `MongoPmsWriteClaimStore` used by the concurrency tests (no embedded Mongo here), so Mongo-level index behaviour is not tested. `ezee/EzeeClientTest` also covers `recordCafeCall`.
- Hub read tests use stubbed gateways with synthetic values and real field names; Log-capture PII checks exist in the booking, guest, housekeeping and reconcile tests. Role matrix is asserted by exception type, not HTTP status. `AppVersionControllerTest` does not assert 401 at runtime.
- eZee/charge-post edits: run at least EzeeChargePostServiceTest, StaleChargePostRecoveryTest, OrderRepositoryClaimTest.
- Coverage tooling (jacoco etc.): none in pom.
