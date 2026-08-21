# App Version / Forced-Update API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the backend a way to record the latest published Android app
version and push an FCM notification to staff devices when it changes.

**Architecture:** One Mongo-backed `AppVersion` singleton document (fixed id
`"latest"`), a service enforcing monotonic `versionCode`, a controller
exposing a public read endpoint and an authenticated publish endpoint, and a
new method on the existing `FCMNotificationService` reusing the existing
`new_orders` topic with a new `APP_UPDATE` message type.

**Tech Stack:** Spring Boot 3.5.10, Java 17, Spring Data MongoDB, Lombok,
JUnit 5 + Mockito (existing test stack), Firebase Admin SDK (already wired).

**Related spec:** `docs/superpowers/specs/2026-08-21-forced-app-update-design.md`
in `mosaichostels-cafe_android` (cross-repo; this plan implements the backend
half). Note: the spec's `/api/app/...` paths are corrected to `/app/...` in
this plan — the codebase has no `/api` prefix on any existing endpoint
(`/orders`, `/config`, `/auth/login`, etc.), and `/app/...` matches that
convention.

## Global Constraints

- No `/api` prefix on routes — match existing convention (`/orders`, `/config`).
- No new authorization/role system — `POST /app/publish` uses the existing
  "any authenticated user" pattern (`anyRequest().authenticated()` in
  `SecurityConfig`); the codebase has no role/permission model to hook into.
- `GET /app/version` must be public (`permitAll`) — the Android app calls it
  from `MainActivity.onResume()` before any update gating, must work even on
  a stale/broken token.
- Reuse `FCMNotificationService`'s existing `ORDERS_TOPIC` ("new_orders") —
  do not create a new FCM topic.
- Lombok `@Data`/`@NoArgsConstructor`/`@AllArgsConstructor` for the new model
  (matches `ErrorResponse`/`AuditLog`, not the manual-getter style of `Category`).

---

### Task 1: `AppVersion` model

**Files:**
- Create: `src/main/java/com/hostel/ordering/model/AppVersion.java`

**Interfaces:**
- Produces: `AppVersion` class with fields `id` (String), `versionCode` (int),
  `versionName` (String), `downloadUrl` (String), `releaseNotes` (String),
  `publishedAt` (long). Lombok `@Data @NoArgsConstructor @AllArgsConstructor`.
  `@Document(collection = "app_version")`, `@Id` on `id`.

- [ ] **Step 1: Create the model**

```java
package com.hostel.ordering.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "app_version")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppVersion {

    public static final String LATEST_ID = "latest";

    @Id
    private String id;
    private int versionCode;
    private String versionName;
    private String downloadUrl;
    private String releaseNotes;
    private long publishedAt;
}
```

No test — pure data class, matches the untested-model convention already in
this codebase (`Category`, `Order`, `MenuItem` have no tests either).

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/hostel/ordering/model/AppVersion.java
git commit -m "feat: add AppVersion model"
```

---

### Task 2: `AppVersionRepository`

**Files:**
- Create: `src/main/java/com/hostel/ordering/repository/AppVersionRepository.java`

**Interfaces:**
- Consumes: `AppVersion` (Task 1)
- Produces: `AppVersionRepository extends MongoRepository<AppVersion, String>`
  — standard `findById`/`save`, no custom queries needed.

- [ ] **Step 1: Create the repository**

```java
package com.hostel.ordering.repository;

import com.hostel.ordering.model.AppVersion;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AppVersionRepository extends MongoRepository<AppVersion, String> {
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/hostel/ordering/repository/AppVersionRepository.java
git commit -m "feat: add AppVersionRepository"
```

---

### Task 3: `FCMNotificationService.sendAppUpdateNotification`

**Files:**
- Modify: `src/main/java/com/hostel/ordering/service/FCMNotificationService.java`

**Interfaces:**
- Produces: `void sendAppUpdateNotification(String versionName, String downloadUrl, String releaseNotes)`
  — `@Async`, sends an FCM data message to `ORDERS_TOPIC` with
  `type=APP_UPDATE`, `versionName`, `downloadUrl`, `releaseNotes`, `title`, `body`.

No dedicated unit test — matches the existing convention where
`sendNewOrderNotification`/`sendOrderCancelledNotification` in this same
class have no tests (the class has zero test coverage today; this method
follows the identical pattern, so isn't introducing a new untested surface).

- [ ] **Step 1: Add the method**

Add to `FCMNotificationService`, after `sendOrderCancelledNotification`:

```java
    @Async
    public void sendAppUpdateNotification(String versionName, String downloadUrl, String releaseNotes) {
        try {
            String title = "🔄 App Update Required";
            String body = "Version " + versionName + " is now required. Tap to update.";

            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(3600 * 1000L)
                    .build();

            Message message = Message.builder()
                    .setTopic(ORDERS_TOPIC)
                    .setAndroidConfig(androidConfig)
                    .putData("type", "APP_UPDATE")
                    .putData("versionName", versionName)
                    .putData("downloadUrl", downloadUrl)
                    .putData("releaseNotes", releaseNotes != null ? releaseNotes : "")
                    .putData("title", title)
                    .putData("body", body)
                    .build();

            String response = firebaseMessaging.send(message);
            logger.info("✅ FCM app-update notification sent: {}", response);

        } catch (Exception e) {
            logger.error("❌ Failed to send FCM app-update notification", e);
        }
    }
```

- [ ] **Step 2: Compile check**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hostel/ordering/service/FCMNotificationService.java
git commit -m "feat: add sendAppUpdateNotification to FCMNotificationService"
```

---

### Task 4: `AppVersionService`

**Files:**
- Create: `src/main/java/com/hostel/ordering/service/AppVersionService.java`
- Test: `src/test/java/com/hostel/ordering/service/AppVersionServiceTest.java`

**Interfaces:**
- Consumes: `AppVersionRepository` (Task 2), `FCMNotificationService.sendAppUpdateNotification` (Task 3)
- Produces:
  - `AppVersion getLatest()` — returns `null` if none published yet.
  - `AppVersion publish(AppVersion request)` — validates `request.versionCode`
    is strictly greater than the current latest's (if one exists), saves with
    id `AppVersion.LATEST_ID` and `publishedAt = System.currentTimeMillis()`,
    calls `sendAppUpdateNotification`, returns the saved entity. Throws
    `IllegalArgumentException` on a non-increasing `versionCode` (caught by
    the existing `GlobalExceptionHandler` → HTTP 400).

- [ ] **Step 1: Write failing tests**

```java
package com.hostel.ordering.service;

import com.hostel.ordering.model.AppVersion;
import com.hostel.ordering.repository.AppVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppVersionServiceTest {

    @Mock
    AppVersionRepository appVersionRepository;

    @Mock
    FCMNotificationService fcmNotificationService;

    AppVersionService appVersionService;

    @BeforeEach
    void setUp() {
        appVersionService = new AppVersionService(appVersionRepository, fcmNotificationService);
    }

    @Test
    void getLatest_returnsNull_whenNothingPublished() {
        when(appVersionRepository.findById(AppVersion.LATEST_ID)).thenReturn(Optional.empty());

        assertNull(appVersionService.getLatest());
    }

    @Test
    void getLatest_returnsStoredVersion() {
        AppVersion stored = new AppVersion(AppVersion.LATEST_ID, 5, "1.5", "http://x/app.apk", "notes", 123L);
        when(appVersionRepository.findById(AppVersion.LATEST_ID)).thenReturn(Optional.of(stored));

        assertEquals(stored, appVersionService.getLatest());
    }

    @Test
    void publish_savesAndNotifies_whenNoExistingVersion() {
        when(appVersionRepository.findById(AppVersion.LATEST_ID)).thenReturn(Optional.empty());
        AppVersion request = new AppVersion(null, 2, "1.1", "http://x/app.apk", "fix", 0L);
        when(appVersionRepository.save(any(AppVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        AppVersion result = appVersionService.publish(request);

        assertEquals(AppVersion.LATEST_ID, result.getId());
        assertEquals(2, result.getVersionCode());
        assertTrue(result.getPublishedAt() > 0);

        ArgumentCaptor<AppVersion> captor = ArgumentCaptor.forClass(AppVersion.class);
        verify(appVersionRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getVersionCode());

        verify(fcmNotificationService).sendAppUpdateNotification("1.1", "http://x/app.apk", "fix");
    }

    @Test
    void publish_rejectsNonIncreasingVersionCode() {
        AppVersion existing = new AppVersion(AppVersion.LATEST_ID, 5, "1.5", "http://x/app.apk", "notes", 123L);
        when(appVersionRepository.findById(AppVersion.LATEST_ID)).thenReturn(Optional.of(existing));

        AppVersion request = new AppVersion(null, 5, "1.5-again", "http://x/app.apk", "notes", 0L);

        assertThrows(IllegalArgumentException.class, () -> appVersionService.publish(request));
        verify(appVersionRepository, never()).save(any());
        verify(fcmNotificationService, never()).sendAppUpdateNotification(any(), any(), any());
    }
}
```

- [ ] **Step 2: Run tests, verify they fail to compile (AppVersionService doesn't exist yet)**

Run: `mvn -q -Dtest=AppVersionServiceTest test`
Expected: COMPILATION ERROR — `AppVersionService` does not exist.

- [ ] **Step 3: Implement `AppVersionService`**

```java
package com.hostel.ordering.service;

import com.hostel.ordering.model.AppVersion;
import com.hostel.ordering.repository.AppVersionRepository;
import org.springframework.stereotype.Service;

@Service
public class AppVersionService {

    private final AppVersionRepository appVersionRepository;
    private final FCMNotificationService fcmNotificationService;

    public AppVersionService(AppVersionRepository appVersionRepository,
                              FCMNotificationService fcmNotificationService) {
        this.appVersionRepository = appVersionRepository;
        this.fcmNotificationService = fcmNotificationService;
    }

    public AppVersion getLatest() {
        return appVersionRepository.findById(AppVersion.LATEST_ID).orElse(null);
    }

    public AppVersion publish(AppVersion request) {
        AppVersion existing = getLatest();
        if (existing != null && request.getVersionCode() <= existing.getVersionCode()) {
            throw new IllegalArgumentException(
                    "versionCode must be greater than current latest (" + existing.getVersionCode() + ")");
        }

        AppVersion toSave = new AppVersion(
                AppVersion.LATEST_ID,
                request.getVersionCode(),
                request.getVersionName(),
                request.getDownloadUrl(),
                request.getReleaseNotes(),
                System.currentTimeMillis());

        AppVersion saved = appVersionRepository.save(toSave);
        fcmNotificationService.sendAppUpdateNotification(
                saved.getVersionName(), saved.getDownloadUrl(), saved.getReleaseNotes());
        return saved;
    }
}
```

- [ ] **Step 4: Run tests, verify they pass**

Run: `mvn -q -Dtest=AppVersionServiceTest test`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hostel/ordering/service/AppVersionService.java \
        src/test/java/com/hostel/ordering/service/AppVersionServiceTest.java
git commit -m "feat: add AppVersionService with monotonic versionCode enforcement"
```

---

### Task 5: `AppVersionController`

**Files:**
- Create: `src/main/java/com/hostel/ordering/controller/AppVersionController.java`

**Interfaces:**
- Consumes: `AppVersionService` (Task 4)
- Produces:
  - `GET /app/version` → 200 with `AppVersion` body, or 404 if none published.
  - `POST /app/publish` → 200 with saved `AppVersion` body, request body is
    an `AppVersion` JSON (`versionCode`, `versionName`, `downloadUrl`,
    `releaseNotes` — `id`/`publishedAt` ignored if present).

No controller test — matches the existing convention (`ConfigController`,
`AuthController`, `OrderController` all have zero tests; only the service
layer is tested in this codebase).

- [ ] **Step 1: Create the controller**

```java
package com.hostel.ordering.controller;

import com.hostel.ordering.model.AppVersion;
import com.hostel.ordering.service.AppVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app")
public class AppVersionController {

    private final AppVersionService appVersionService;

    public AppVersionController(AppVersionService appVersionService) {
        this.appVersionService = appVersionService;
    }

    @GetMapping("/version")
    public ResponseEntity<AppVersion> getLatestVersion() {
        AppVersion latest = appVersionService.getLatest();
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latest);
    }

    @PostMapping("/publish")
    public ResponseEntity<AppVersion> publish(@RequestBody AppVersion request) {
        return ResponseEntity.ok(appVersionService.publish(request));
    }
}
```

- [ ] **Step 2: Compile check**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hostel/ordering/controller/AppVersionController.java
git commit -m "feat: add AppVersionController with GET /app/version and POST /app/publish"
```

---

### Task 6: Wire `GET /app/version` as public in `SecurityConfig`

**Files:**
- Modify: `src/main/java/com/hostel/ordering/config/SecurityConfig.java:55-61` (the `authorizeHttpRequests` block)

**Interfaces:**
- Consumes: nothing new
- Produces: nothing new — pure config change. `POST /app/publish` requires
  no change; it already falls under `.anyRequest().authenticated()`.

- [ ] **Step 1: Add the permitAll rule**

In the `authorizeHttpRequests` lambda, add one line alongside the existing
`GET /config` and `GET /menu-items/**` rules:

```java
                        .requestMatchers(HttpMethod.GET, "/app/version").permitAll()
```

Full block after the change:

```java
                .authorizeHttpRequests(auth -> auth.requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/config").permitAll()
                        .requestMatchers(HttpMethod.GET, "/menu-items/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/other-essentials/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/app/version").permitAll()
                        .anyRequest().authenticated());
```

- [ ] **Step 2: Compile check**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Manual smoke test**

Run the app locally (with `MONGODB_URI`, `config.jwtSecret`, and
`FIREBASE_SERVICE_ACCOUNT_JSON` env vars set — see existing `README`/deploy
config for values), then:

```bash
# Before any publish: expect 404
curl -i http://localhost:7860/app/version

# Log in to get a token (use existing seeded admin credentials)
TOKEN=$(curl -s -X POST http://localhost:7860/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"<admin>","password":"<password>"}' | jq -r .token)

# Publish v2
curl -i -X POST http://localhost:7860/app/publish \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"versionCode":2,"versionName":"1.1","downloadUrl":"https://example.com/app.apk","releaseNotes":"test"}'

# Expect 200 with the saved AppVersion JSON

# Now unauthenticated read should return it
curl -i http://localhost:7860/app/version
# Expect 200 with versionCode 2

# Publish a downgrade — expect 400
curl -i -X POST http://localhost:7860/app/publish \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"versionCode":1,"versionName":"1.0","downloadUrl":"https://example.com/app.apk","releaseNotes":"test"}'
```

Expected: 404 → 200 → 200 (versionCode 2) → 400, matching each step above.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hostel/ordering/config/SecurityConfig.java
git commit -m "feat: expose GET /app/version publicly"
```

---

## Self-Review Notes

- **Spec coverage:** Backend model/repository/service/controller/FCM method
  and public-read wiring — all covered (Tasks 1–6). APK hosting, GitHub
  releases repo, and the Android-side blocking UI belong to the Android
  plan, not this one.
- **Path correction:** spec said `/api/app/...`; corrected to `/app/...`
  throughout to match this codebase's actual convention (confirmed via
  `SecurityConfig`/`ConfigController`/`AuthController` — none use `/api`).
- **Type consistency:** `AppVersionService` constructor signature
  `(AppVersionRepository, FCMNotificationService)` matches what
  `AppVersionServiceTest` and `AppVersionController` both call it with.
