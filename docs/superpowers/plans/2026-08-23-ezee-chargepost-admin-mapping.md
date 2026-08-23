# eZee Chargepost — Admin-Mapped Posting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin, from the existing billing web (`mosaichostels-cafe_frontend`),
search eZee's live guest/room data and post a `DELIVERED` cafe order's charge
to the correct folio in one action, which also marks the order `CHECKED`.
No automatic posting anywhere — every charge is a deliberate, admin-confirmed
action.

**Architecture:** A new `ezee` package with two collaborators: `EzeeClient`
(HTTP + XML transport to the `service.pos2pms` endpoint) and
`EzeeChargePostService` (given an admin-picked room number, resolves the live
folio via `roomquery` and posts the charge; also voids a posted charge on
cancel). Two new `OrderController` endpoints: a read-only eZee search
(`GET /orders/{id}/ezee-candidates`, wraps `roomlist`/`roomquery`) and the
post action (`POST /orders/{id}/chargepost`). `admin.js`'s existing
"Mark as Checked" button becomes "Post & Check", opening a search-and-pick
modal. `OrderService.createOrder()` is untouched — no eZee call at order
creation. `updateOrderStatus()` keeps a `CANCELLED`-time auto-void for
already-posted orders.

**Tech Stack:** Spring Boot 3.5.10, Java 17, Spring Data MongoDB, JUnit 5 +
Mockito (existing test stack). No new Maven dependencies — HTTP via
`java.net.http.HttpClient`, XML via `javax.xml.parsers`/`javax.xml.transform`
(JDK standard library). Frontend: vanilla JS, existing `API`/`AdminApp`
pattern in `mosaichostels-cafe_frontend/js/`.

**Related docs:**
- `docs/superpowers/specs/2026-08-23-ezee-chargepost-admin-mapping-design.md`
  — the approved design this plan implements.
- `Website/docs/eZee-Connectivity-API.md` lines 4061–4406 — authoritative
  `chargepost`/`voidcharge`/`roomlist`/`roomquery` spec.
- Supersedes `docs/superpowers/plans/2026-08-22-ezee-chargepost-integration.md`
  in full — that plan's automatic room resolution, order-creation trigger,
  and scheduled retry are dropped. Its XML/transport tasks are reused
  verbatim below (Tasks 1–2).

## Global Constraints

- No new Maven dependencies.
- No `/api` prefix on routes (existing convention).
- Config via `@Value("${ezee.xxx:default}")` reading env vars, matching the
  existing `config.jwtSecret` / `cors.allowed-origins` pattern in
  `application-prod.yml`.
- An `EZEE_MOCK=true` env flag must short-circuit `EzeeClient` to canned
  in-memory responses — lets this be developed/tested without live eZee
  credentials.
- `chargepost`/`roomlist`/`roomquery` request and response bodies are XML —
  build and parse via JDK `javax.xml.parsers.DocumentBuilder` /
  `javax.xml.transform.Transformer`, never string concatenation (guest names
  and menu item names are free text and must be XML-escaped).
- Chargepost/void never throw out of `EzeeChargePostService` — every failure
  path is captured into `chargePostStatus`/`chargePostError` on the `Order`
  and returned to the caller, never propagated as an exception.

---

### Task 1: `EzeeXmlUtil` — build request XML / parse response XML

**Files:**
- Create: `src/main/java/com/hostel/ordering/ezee/EzeeXmlUtil.java`
- Test: `src/test/java/com/hostel/ordering/ezee/EzeeXmlUtilTest.java`

**Interfaces:**
- Produces:
  `EzeeXmlUtil.buildRequest(LinkedHashMap<String,String> fields) -> String`
  (serializes to `<request><key>value</key>...</request>`, XML-escaped);
  `EzeeXmlUtil.parseFlatResponse(String xml) -> Map<String,String>` (top-level
  child elements only — covers `chargepost`/`voidcharge` responses and
  `roomquery`'s top-level fields);
  `EzeeXmlUtil.parseRoomRows(String xml) -> List<Map<String,String>>` (each
  `<roomrows><row>...</row></roomrows>` entry as a flat map — covers
  `roomlist` and `roomquery`'s nested `roomrows`).
- Consumes: nothing (pure XML in/out, no other class dependencies).

- [ ] **Step 1: Write failing test**

```java
package com.hostel.ordering.ezee;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EzeeXmlUtilTest {

    @Test
    void buildRequest_escapesAndOrdersFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("auth", "abc123");
        fields.put("oprn", "chargepost");
        fields.put("remark", "Tea & Toast");

        String xml = EzeeXmlUtil.buildRequest(fields);

        assertTrue(xml.startsWith("<?xml version=\"1.0\" standalone=\"yes\"?>"));
        assertTrue(xml.contains("<auth>abc123</auth>"));
        assertTrue(xml.contains("<oprn>chargepost</oprn>"));
        assertTrue(xml.contains("<remark>Tea &amp; Toast</remark>"));
        assertTrue(xml.indexOf("<auth>") < xml.indexOf("<oprn>"));
    }

    @Test
    void parseFlatResponse_readsTopLevelFields() {
        String xml = "<?xml version='1.0' standalone='yes'?>"
                + "<response><status>ok</status><msg>added in queue</msg><requestid>2805</requestid></response>";

        Map<String, String> result = EzeeXmlUtil.parseFlatResponse(xml);

        assertEquals("ok", result.get("status"));
        assertEquals("added in queue", result.get("msg"));
        assertEquals("2805", result.get("requestid"));
    }

    @Test
    void parseRoomRows_readsEachRowAsFlatMap() {
        String xml = "<?xml version='1.0' standalone='yes'?><response><status>ok</status>"
                + "<roomrows>"
                + "<row><guestname>Mr. Joy</guestname><room>106</room><masterfolio>10</masterfolio><roomtype>Studio</roomtype></row>"
                + "<row><guestname>Mrs Sophia</guestname><room>109</room><masterfolio>22</masterfolio><roomtype>Single Bedroom Suite</roomtype></row>"
                + "</roomrows></response>";

        List<Map<String, String>> rows = EzeeXmlUtil.parseRoomRows(xml);

        assertEquals(2, rows.size());
        assertEquals("Mr. Joy", rows.get(0).get("guestname"));
        assertEquals("106", rows.get(0).get("room"));
        assertEquals("10", rows.get(0).get("masterfolio"));
        assertEquals("Studio", rows.get(0).get("roomtype"));
        assertEquals("Mrs Sophia", rows.get(1).get("guestname"));
    }
}
```

- [ ] **Step 2: Run test, verify fails**
  Run: `mvn -q -Dtest=EzeeXmlUtilTest test`
  Expected: FAIL — `EzeeXmlUtil` does not exist

- [ ] **Step 3: Implement**

```java
package com.hostel.ordering.ezee;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Builds/parses the flat single-level XML the eZee POS2PMS API uses for
// chargepost/voidcharge/roomlist/roomquery. Uses JDK DOM, not string
// concatenation, so field values (guest names, menu item names) are
// always correctly XML-escaped.
public final class EzeeXmlUtil {

    private EzeeXmlUtil() {}

    public static String buildRequest(LinkedHashMap<String, String> fields) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement("request");
            doc.appendChild(root);
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                Element el = doc.createElement(entry.getKey());
                el.setTextContent(entry.getValue() == null ? "" : entry.getValue());
                root.appendChild(el);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));

            return "<?xml version=\"1.0\" standalone=\"yes\"?>" + writer;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build eZee request XML", e);
        }
    }

    public static Map<String, String> parseFlatResponse(String xml) {
        Element root = parseRoot(xml);
        Map<String, String> result = new LinkedHashMap<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) node;
            if ("roomrows".equals(el.getTagName())) continue;
            result.put(el.getTagName(), el.getTextContent());
        }
        return result;
    }

    public static List<Map<String, String>> parseRoomRows(String xml) {
        Element root = parseRoot(xml);
        List<Map<String, String>> rows = new ArrayList<>();
        NodeList rowNodes = root.getElementsByTagName("row");
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element rowEl = (Element) rowNodes.item(i);
            Map<String, String> row = new LinkedHashMap<>();
            NodeList fields = rowEl.getChildNodes();
            for (int j = 0; j < fields.getLength(); j++) {
                Node node = fields.item(j);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) node;
                row.put(el.getTagName(), el.getTextContent());
            }
            rows.add(row);
        }
        return rows;
    }

    private static Element parseRoot(String xml) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse eZee response XML", e);
        }
    }
}
```

- [ ] **Step 4: Run test, verify passes**
  Run: `mvn -q -Dtest=EzeeXmlUtilTest test`
  Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hostel/ordering/ezee/EzeeXmlUtil.java src/test/java/com/hostel/ordering/ezee/EzeeXmlUtilTest.java
git commit -m "feat: add EzeeXmlUtil for POS2PMS request/response XML"
```

---

### Task 2: `EzeeClient` — HTTP transport + mock mode

**Files:**
- Create: `src/main/java/com/hostel/ordering/ezee/EzeeClient.java`
- Create: `src/main/java/com/hostel/ordering/ezee/EzeeMockResponses.java`
- Test: `src/test/java/com/hostel/ordering/ezee/EzeeClientTest.java`

**Interfaces:**
- Consumes: `EzeeXmlUtil.buildRequest`/`parseFlatResponse`/`parseRoomRows` (Task 1).
- Produces:
  `EzeeClient.post(LinkedHashMap<String,String> fields) -> Map<String,String>`
  and `EzeeClient.postForRoomRows(LinkedHashMap<String,String> fields) -> List<Map<String,String>>`
  — post to the configured endpoint (or return a mock when `ezee.mock=true`).
  Used by the eZee search endpoint (Task 4) and `EzeeChargePostService`
  (Task 3) for every `oprn` (`roomlist`, `roomquery`, `chargepost`, `voidcharge`).
  `EzeeClient.getAuthCode() -> String` — used by both.

- [ ] **Step 1: Write failing test**

Uses JDK's built-in `com.sun.net.httpserver.HttpServer` as a local stub — no
new test dependency.

```java
package com.hostel.ordering.ezee;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EzeeClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void post_sendsXmlAndParsesFlatResponse() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pos2pms", exchange -> {
            String responseXml = "<?xml version='1.0' standalone='yes'?>"
                    + "<response><status>ok</status><msg>added in queue</msg><requestid>2805</requestid></response>";
            byte[] bytes = responseXml.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/pos2pms";

        EzeeClient client = new EzeeClient(endpoint, "auth-code", false);
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("auth", "auth-code");
        fields.put("oprn", "chargepost");

        Map<String, String> result = client.post(fields);

        assertEquals("ok", result.get("status"));
        assertEquals("2805", result.get("requestid"));
    }

    @Test
    void post_mockMode_returnsCannedRoomlistWithoutNetworkCall() {
        EzeeClient client = new EzeeClient("http://unused.invalid", "auth-code", true);
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("oprn", "roomlist");

        Map<String, String> result = client.post(fields);

        assertEquals("ok", result.get("status"));
    }

    @Test
    void postForRoomRows_mockMode_returnsCannedRows() {
        EzeeClient client = new EzeeClient("http://unused.invalid", "auth-code", true);
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("oprn", "roomlist");

        java.util.List<Map<String, String>> rows = client.postForRoomRows(fields);

        assertFalse(rows.isEmpty());
        assertEquals("Mock Guest", rows.get(0).get("guestname"));
    }
}
```

- [ ] **Step 2: Run test, verify fails**
  Run: `mvn -q -Dtest=EzeeClientTest test`
  Expected: FAIL — `EzeeClient` does not exist

- [ ] **Step 3: Implement**

```java
package com.hostel.ordering.ezee;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Low-level transport for eZee's POS2PMS XML API. One job: turn a field map
// into an HTTP POST and the response body into a field map. Callers (the
// admin eZee-search endpoint, EzeeChargePostService) live elsewhere.
@Component
public class EzeeClient {

    private final String endpoint;
    private final String authCode;
    private final boolean mock;
    private final HttpClient httpClient;

    public EzeeClient(
            @Value("${ezee.endpoint:https://live.ipms247.com/index.php/page/service.pos2pms}") String endpoint,
            @Value("${ezee.auth-code:}") String authCode,
            @Value("${ezee.mock:false}") boolean mock) {
        this.endpoint = endpoint;
        this.authCode = authCode;
        this.mock = mock;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String getAuthCode() {
        return authCode;
    }

    public Map<String, String> post(LinkedHashMap<String, String> fields) {
        if (mock) {
            return EzeeMockResponses.forOprn(fields.get("oprn"));
        }

        String requestXml = EzeeXmlUtil.buildRequest(fields);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofString(requestXml))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return EzeeXmlUtil.parseFlatResponse(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("eZee request failed for oprn=" + fields.get("oprn"), e);
        }
    }

    public List<Map<String, String>> postForRoomRows(LinkedHashMap<String, String> fields) {
        if (mock) {
            return EzeeMockResponses.roomRowsForOprn(fields.get("oprn"));
        }

        String requestXml = EzeeXmlUtil.buildRequest(fields);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofString(requestXml))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return EzeeXmlUtil.parseRoomRows(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("eZee request failed for oprn=" + fields.get("oprn"), e);
        }
    }
}
```

```java
package com.hostel.ordering.ezee;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Canned responses for local/dev use when ezee.mock=true — lets this
// integration be developed and its admin-search UI clicked through without
// live eZee credentials.
final class EzeeMockResponses {

    private EzeeMockResponses() {}

    static Map<String, String> forOprn(String oprn) {
        Map<String, String> result = new LinkedHashMap<>();
        if ("chargepost".equals(oprn)) {
            result.put("status", "ok");
            result.put("msg", "added in queue");
            result.put("requestid", "999999");
        } else if ("roomquery".equals(oprn)) {
            result.put("status", "ok");
            result.put("msg", "inhouse");
            result.put("guestname", "Mock Guest");
            result.put("room", "101");
            result.put("masterfolio", "8");
        } else {
            result.put("status", "ok");
            result.put("msg", "inhouse");
        }
        return result;
    }

    static List<Map<String, String>> roomRowsForOprn(String oprn) {
        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> row1 = new LinkedHashMap<>();
        row1.put("guestname", "Mock Guest");
        row1.put("room", "106");
        row1.put("masterfolio", "10");
        row1.put("roomtype", "8 - Bed Mixed Dorm");
        rows.add(row1);
        Map<String, String> row2 = new LinkedHashMap<>();
        row2.put("guestname", "Second Mock Guest");
        row2.put("room", "107");
        row2.put("masterfolio", "11");
        row2.put("roomtype", "8 - Bed Mixed Dorm");
        rows.add(row2);
        return rows;
    }
}
```

- [ ] **Step 4: Run test, verify passes**
  Run: `mvn -q -Dtest=EzeeClientTest test`
  Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hostel/ordering/ezee/EzeeClient.java src/main/java/com/hostel/ordering/ezee/EzeeMockResponses.java src/test/java/com/hostel/ordering/ezee/EzeeClientTest.java
git commit -m "feat: add EzeeClient HTTP transport with mock mode"
```

---

### Task 3: `Order` chargepost outcome fields

**Files:**
- Modify: `src/main/java/com/hostel/ordering/model/Order.java`
- Test: `src/test/java/com/hostel/ordering/model/OrderTest.java`

**Interfaces:**
- Produces: `Order.getChargePostStatus()/setChargePostStatus(String)` (null |
  `"QUEUED"` | `"FAILED"` | `"VOIDED"`), `getChargePostRequestId()/set...(String)`,
  `getChargePostError()/set...(String)`, `getChargePostRoom()/set...(String)`,
  `getChargePostFolio()/set...(String)`, `getChargePostAt()/set...(Long)` —
  all used by `EzeeChargePostService` (Task 4) and `OrderService` (Task 6).

- [ ] **Step 1: Write failing test**

```java
package com.hostel.ordering.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    @Test
    void chargePostFields_defaultToNull_andAreSettable() {
        Order order = new Order();
        assertNull(order.getChargePostStatus());
        assertNull(order.getChargePostRequestId());
        assertNull(order.getChargePostError());
        assertNull(order.getChargePostRoom());
        assertNull(order.getChargePostFolio());
        assertNull(order.getChargePostAt());

        order.setChargePostStatus("QUEUED");
        order.setChargePostRequestId("2805");
        order.setChargePostError(null);
        order.setChargePostRoom("101");
        order.setChargePostFolio("8");
        order.setChargePostAt(1700000000000L);

        assertEquals("QUEUED", order.getChargePostStatus());
        assertEquals("2805", order.getChargePostRequestId());
        assertEquals("101", order.getChargePostRoom());
        assertEquals("8", order.getChargePostFolio());
        assertEquals(1700000000000L, order.getChargePostAt());
    }
}
```

- [ ] **Step 2: Run test, verify fails**
  Run: `mvn -q -Dtest=OrderTest test`
  Expected: FAIL — `getChargePostStatus` does not exist

- [ ] **Step 3: Add the fields**

Add these six fields plus their getters/setters to the existing `Order`
class (`src/main/java/com/hostel/ordering/model/Order.java`), alongside the
existing `chargePostAt`-adjacent block — do not otherwise touch the class:

```java
    // eZee chargepost outcome — set only when an admin posts via
    // POST /orders/{id}/chargepost, reversed to VOIDED on cancel.
    // Null status = never posted. "QUEUED" = eZee accepted it (status=ok).
    // "FAILED" = eZee rejected it or the picked room had no live folio
    // (see chargePostError). "VOIDED" = order was cancelled after posting
    // and the charge was reversed.
    private String chargePostStatus;
    private String chargePostRequestId;
    private String chargePostError;
    private String chargePostRoom;
    private String chargePostFolio;
    private Long chargePostAt;

    public String getChargePostStatus() { return chargePostStatus; }
    public void setChargePostStatus(String chargePostStatus) { this.chargePostStatus = chargePostStatus; }

    public String getChargePostRequestId() { return chargePostRequestId; }
    public void setChargePostRequestId(String chargePostRequestId) { this.chargePostRequestId = chargePostRequestId; }

    public String getChargePostError() { return chargePostError; }
    public void setChargePostError(String chargePostError) { this.chargePostError = chargePostError; }

    public String getChargePostRoom() { return chargePostRoom; }
    public void setChargePostRoom(String chargePostRoom) { this.chargePostRoom = chargePostRoom; }

    public String getChargePostFolio() { return chargePostFolio; }
    public void setChargePostFolio(String chargePostFolio) { this.chargePostFolio = chargePostFolio; }

    public Long getChargePostAt() { return chargePostAt; }
    public void setChargePostAt(Long chargePostAt) { this.chargePostAt = chargePostAt; }
```

- [ ] **Step 4: Run test, verify passes**
  Run: `mvn -q -Dtest=OrderTest test`
  Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hostel/ordering/model/Order.java src/test/java/com/hostel/ordering/model/OrderTest.java
git commit -m "feat: add chargepost outcome fields to Order"
```

---

### Task 4: `EzeeChargePostService` — admin-driven post + void

**Files:**
- Create: `src/main/java/com/hostel/ordering/ezee/EzeeChargePostService.java`
- Test: `src/test/java/com/hostel/ordering/ezee/EzeeChargePostServiceTest.java`

**Interfaces:**
- Consumes: `EzeeClient.post` (Task 2), `Order` chargepost fields (Task 3).
- Produces:
  - `EzeeChargePostService.post(Order order, String room) -> Order` — resolves
    the live folio for the admin-picked `room` via `roomquery`, posts the
    charge, mutates and returns the same `Order` instance with `chargePost*`
    fields set. Does NOT save — the caller (`OrderService`, Task 6) owns
    persistence.
  - `EzeeChargePostService.voidPost(Order order) -> Order` — sends
    `oprn=voidcharge` with `order.getChargePostRequestId()`, mutates and
    returns the same `Order` (does NOT save). Sets `chargePostStatus="VOIDED"`
    on success; on failure leaves `chargePostStatus` untouched (still
    `"QUEUED"`) and records the reason in `chargePostError`.
  - Neither method throws — every failure path lands in
    `chargePostStatus`/`chargePostError`.

- [ ] **Step 1: Write failing test**

```java
package com.hostel.ordering.ezee;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EzeeChargePostServiceTest {

    @Mock
    EzeeClient ezeeClient;

    private EzeeChargePostService service;

    private Order sampleOrder() {
        Order order = new Order();
        order.setId("order123");
        order.setBookingName("Denial Mark");
        order.setDormitory("8 - Bed Mixed Dorm");
        order.setUpdatedBy("staff1");
        order.setTotalAmount(250.0);
        OrderItem item = new OrderItem();
        item.setMenuItemName("Aloo Paratha");
        item.setQuantity(2);
        item.setSubtotal(160.0);
        order.setItems(List.of(item));
        return order;
    }

    @Test
    void post_roomHasLiveFolio_chargepostSucceeds_marksQueued() {
        service = new EzeeChargePostService(ezeeClient, "Cafe");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        roomqueryResponse.put("guestname", "Denial Mark");
        roomqueryResponse.put("masterfolio", "8");
        when(ezeeClient.post(argWithOprn("roomquery"))).thenReturn(roomqueryResponse);

        Map<String, String> chargepostResponse = new LinkedHashMap<>();
        chargepostResponse.put("status", "ok");
        chargepostResponse.put("msg", "added in queue");
        chargepostResponse.put("requestid", "2805");
        when(ezeeClient.post(argWithOprn("chargepost"))).thenReturn(chargepostResponse);

        Order result = service.post(sampleOrder(), "106");

        assertEquals("QUEUED", result.getChargePostStatus());
        assertEquals("2805", result.getChargePostRequestId());
        assertEquals("106", result.getChargePostRoom());
        assertEquals("8", result.getChargePostFolio());
        assertNull(result.getChargePostError());
        assertNotNull(result.getChargePostAt());
    }

    @Test
    void post_roomHasNoLiveFolio_marksFailedWithoutCallingChargepost() {
        service = new EzeeChargePostService(ezeeClient, "Cafe");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "error");
        roomqueryResponse.put("msg", "Room not occupied");
        when(ezeeClient.post(argWithOprn("roomquery"))).thenReturn(roomqueryResponse);

        Order result = service.post(sampleOrder(), "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("Room not occupied", result.getChargePostError());
        assertNull(result.getChargePostRequestId());
    }

    @Test
    void post_ezeeReturnsChargepostError_marksFailed() {
        service = new EzeeChargePostService(ezeeClient, "Cafe");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        roomqueryResponse.put("guestname", "Denial Mark");
        roomqueryResponse.put("masterfolio", "8");
        when(ezeeClient.post(argWithOprn("roomquery"))).thenReturn(roomqueryResponse);

        Map<String, String> chargepostResponse = new LinkedHashMap<>();
        chargepostResponse.put("status", "error");
        chargepostResponse.put("msg", "Folio not found in PMS");
        when(ezeeClient.post(argWithOprn("chargepost"))).thenReturn(chargepostResponse);

        Order result = service.post(sampleOrder(), "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("Folio not found in PMS", result.getChargePostError());
    }

    @Test
    void voidPost_ezeeConfirms_marksVoided() {
        service = new EzeeChargePostService(ezeeClient, "Cafe");
        Order order = sampleOrder();
        order.setChargePostStatus("QUEUED");
        order.setChargePostRequestId("2805");

        Map<String, String> voidResponse = new LinkedHashMap<>();
        voidResponse.put("status", "ok");
        when(ezeeClient.post(argWithOprn("voidcharge"))).thenReturn(voidResponse);

        Order result = service.voidPost(order);

        assertEquals("VOIDED", result.getChargePostStatus());
        assertNull(result.getChargePostError());
    }

    @Test
    void voidPost_ezeeRejects_leavesStatusQueuedAndRecordsError() {
        service = new EzeeChargePostService(ezeeClient, "Cafe");
        Order order = sampleOrder();
        order.setChargePostStatus("QUEUED");
        order.setChargePostRequestId("2805");

        Map<String, String> voidResponse = new LinkedHashMap<>();
        voidResponse.put("status", "error");
        voidResponse.put("msg", "requestid not found");
        when(ezeeClient.post(argWithOprn("voidcharge"))).thenReturn(voidResponse);

        Order result = service.voidPost(order);

        assertEquals("QUEUED", result.getChargePostStatus());
        assertEquals("Void failed: requestid not found", result.getChargePostError());
    }

    private LinkedHashMap<String, String> argWithOprn(String oprn) {
        return org.mockito.ArgumentMatchers.argThat(m -> m != null && oprn.equals(m.get("oprn")));
    }
}
```

- [ ] **Step 2: Run test, verify fails**
  Run: `mvn -q -Dtest=EzeeChargePostServiceTest test`
  Expected: FAIL — `EzeeChargePostService` does not exist

- [ ] **Step 3: Implement**

```java
package com.hostel.ordering.ezee;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Given an admin-picked eZee room number, resolves the live folio and posts
// the order's charge. Mutates and returns the same Order with chargePost*
// fields set; never throws — every failure path lands in
// chargePostStatus=FAILED so the caller's request never has to handle an
// exception, only inspect the returned Order.
@Service
public class EzeeChargePostService {

    private static final Logger log = LoggerFactory.getLogger(EzeeChargePostService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EzeeClient ezeeClient;
    private final String outlet;

    public EzeeChargePostService(EzeeClient ezeeClient, @Value("${ezee.outlet:Cafe}") String outlet) {
        this.ezeeClient = ezeeClient;
        this.outlet = outlet;
    }

    public Order post(Order order, String room) {
        order.setChargePostAt(System.currentTimeMillis());
        try {
            LinkedHashMap<String, String> roomqueryFields = new LinkedHashMap<>();
            roomqueryFields.put("auth", ezeeClient.getAuthCode());
            roomqueryFields.put("oprn", "roomquery");
            roomqueryFields.put("room", room);
            Map<String, String> roomqueryResponse = ezeeClient.post(roomqueryFields);

            if (!"ok".equals(roomqueryResponse.get("status"))) {
                return markFailed(order, roomqueryResponse.getOrDefault("msg", "roomquery failed"));
            }
            String folio = roomqueryResponse.get("masterfolio");

            LinkedHashMap<String, String> chargepostFields = buildChargePostFields(order, room, folio);
            Map<String, String> response = ezeeClient.post(chargepostFields);

            if ("ok".equals(response.get("status"))) {
                order.setChargePostStatus("QUEUED");
                order.setChargePostRequestId(response.get("requestid"));
                order.setChargePostRoom(room);
                order.setChargePostFolio(folio);
                order.setChargePostError(null);
                log.info("Chargepost queued for order {}: requestid={}", order.getId(), response.get("requestid"));
                return order;
            }

            order.setChargePostRoom(room);
            order.setChargePostFolio(folio);
            return markFailed(order, response.getOrDefault("msg", "eZee returned an error"));
        } catch (Exception e) {
            log.error("Chargepost threw for order {}", order.getId(), e);
            return markFailed(order, "Unexpected error: " + e.getMessage());
        }
    }

    private Order markFailed(Order order, String reason) {
        order.setChargePostStatus("FAILED");
        order.setChargePostError(reason);
        order.setChargePostRequestId(null);
        log.warn("Chargepost failed for order {}: {}", order.getId(), reason);
        return order;
    }

    private LinkedHashMap<String, String> buildChargePostFields(Order order, String room, String folio) {
        String today = LocalDate.now().format(DATE_FORMAT);
        String remark = order.getItems().stream()
                .map(OrderItem::getMenuItemName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
        String amount = String.format("%.2f", order.getTotalAmount());

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("auth", ezeeClient.getAuthCode());
        fields.put("oprn", "chargepost");
        fields.put("room", room);
        fields.put("folio", folio);
        fields.put("table", "chargepost");
        fields.put("outlet", outlet);
        fields.put("charge", "Restaurant Charge");
        fields.put("postingdate", today);
        fields.put("trandate", today);
        fields.put("amount", amount);
        fields.put("tax", "0.00");
        fields.put("gross_amount", amount);
        fields.put("voucherno", order.getId());
        fields.put("remark", remark.isEmpty() ? "Cafe order" : remark);
        fields.put("posuser", order.getUpdatedBy() == null ? "system" : order.getUpdatedBy());
        return fields;
    }

    // Reverses a QUEUED chargepost via voidcharge. Never touches
    // chargePostStatus on failure — the charge is still live in eZee, so the
    // Order must keep saying QUEUED rather than claiming a void that didn't
    // happen.
    public Order voidPost(Order order) {
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("auth", ezeeClient.getAuthCode());
            fields.put("oprn", "voidcharge");
            fields.put("requestid", order.getChargePostRequestId());
            Map<String, String> response = ezeeClient.post(fields);

            if ("ok".equals(response.get("status"))) {
                order.setChargePostStatus("VOIDED");
                order.setChargePostError(null);
                log.info("Chargepost voided for order {}: requestid={}", order.getId(), order.getChargePostRequestId());
            } else {
                order.setChargePostError("Void failed: " + response.getOrDefault("msg", "eZee returned an error"));
                log.warn("Chargepost void failed for order {}: {}", order.getId(), order.getChargePostError());
            }
        } catch (Exception e) {
            order.setChargePostError("Void failed: " + e.getMessage());
            log.error("Chargepost void threw for order {}", order.getId(), e);
        }
        return order;
    }
}
```

- [ ] **Step 4: Run test, verify passes**
  Run: `mvn -q -Dtest=EzeeChargePostServiceTest test`
  Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hostel/ordering/ezee/EzeeChargePostService.java src/test/java/com/hostel/ordering/ezee/EzeeChargePostServiceTest.java
git commit -m "feat: add EzeeChargePostService for admin-driven post/void"
```

---

### Task 5: `GET /orders/{id}/ezee-candidates` — admin search endpoint

**Files:**
- Modify: `src/main/java/com/hostel/ordering/controller/OrderController.java`
- Modify: `src/main/java/com/hostel/ordering/service/OrderService.java`
- Test: `src/test/java/com/hostel/ordering/service/OrderServiceTest.java`

**Interfaces:**
- Consumes: `EzeeClient.post`/`postForRoomRows` (Task 2).
- Produces: `OrderService.searchEzeeCandidates(String room, String dormitory)
  -> List<Map<String,String>>` — if `room` is given, wraps a single
  `roomquery(room)` result as a one-row list (empty list if not `status=ok`);
  otherwise wraps `roomlist()`, optionally filtered client-side by
  `dormitory` matching the row's `roomtype` (case-insensitive contains).
  Used by `OrderController.searchEzeeCandidates` and, on the frontend, by the
  "Post & Check" search modal (Task 8).

- [ ] **Step 1: Write failing test**

Add to `OrderServiceTest.java`:

```java
    @Mock
    com.hostel.ordering.ezee.EzeeClient ezeeClient;

    @Test
    void searchEzeeCandidates_byRoom_wrapsRoomqueryAsSingleRow() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, null, ezeeClient);

        java.util.LinkedHashMap<String, String> roomqueryResponse = new java.util.LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        roomqueryResponse.put("guestname", "Denial Mark");
        roomqueryResponse.put("room", "106");
        roomqueryResponse.put("masterfolio", "10");
        when(ezeeClient.post(org.mockito.ArgumentMatchers.argThat(
                m -> m != null && "roomquery".equals(m.get("oprn"))))).thenReturn(roomqueryResponse);

        List<java.util.Map<String, String>> result = svc.searchEzeeCandidates("106", null);

        assertEquals(1, result.size());
        assertEquals("Denial Mark", result.get(0).get("guestname"));
    }

    @Test
    void searchEzeeCandidates_byRoom_roomqueryFails_returnsEmptyList() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, null, ezeeClient);

        java.util.LinkedHashMap<String, String> roomqueryResponse = new java.util.LinkedHashMap<>();
        roomqueryResponse.put("status", "error");
        when(ezeeClient.post(org.mockito.ArgumentMatchers.argThat(
                m -> m != null && "roomquery".equals(m.get("oprn"))))).thenReturn(roomqueryResponse);

        List<java.util.Map<String, String>> result = svc.searchEzeeCandidates("106", null);

        assertTrue(result.isEmpty());
    }

    @Test
    void searchEzeeCandidates_noRoom_returnsWholeRoomlistFilteredByDormitory() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, null, ezeeClient);

        java.util.LinkedHashMap<String, String> row1 = new java.util.LinkedHashMap<>();
        row1.put("guestname", "Mr. Joy");
        row1.put("room", "106");
        row1.put("roomtype", "8 - Bed Mixed Dorm");

        java.util.LinkedHashMap<String, String> row2 = new java.util.LinkedHashMap<>();
        row2.put("guestname", "Mrs Sophia");
        row2.put("room", "201");
        row2.put("roomtype", "101 - Private Room");

        when(ezeeClient.postForRoomRows(org.mockito.ArgumentMatchers.argThat(
                m -> m != null && "roomlist".equals(m.get("oprn")))))
                .thenReturn(List.of(row1, row2));

        List<java.util.Map<String, String>> result = svc.searchEzeeCandidates(null, "8 - Bed Mixed Dorm");

        assertEquals(1, result.size());
        assertEquals("Mr. Joy", result.get(0).get("guestname"));
    }
```

Add `import static org.mockito.Mockito.when;` and
`import com.hostel.ordering.ezee.EzeeChargePostService;` to
`OrderServiceTest.java` if not already present from Task 6's edits (do this
task's tests after Task 6's constructor change, or add both imports now).

- [ ] **Step 2: Run test, verify fails**
  Run: `mvn -q -Dtest=OrderServiceTest test`
  Expected: FAIL — `searchEzeeCandidates` does not exist / constructor arg
  count mismatch

- [ ] **Step 3: Implement**

Add to `OrderService.java` (the `EzeeClient ezeeClient` field/constructor
argument is added once here; Task 6 adds the `EzeeChargePostService` argument
— see Task 6 Step 3 for the combined final constructor):

```java
    public List<Map<String, String>> searchEzeeCandidates(String room, String dormitory) {
        if (room != null && !room.isBlank()) {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("auth", ezeeClient.getAuthCode());
            fields.put("oprn", "roomquery");
            fields.put("room", room);
            Map<String, String> response = ezeeClient.post(fields);
            if (!"ok".equals(response.get("status"))) {
                return List.of();
            }
            return List.of(response);
        }

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("auth", ezeeClient.getAuthCode());
        fields.put("oprn", "roomlist");
        List<Map<String, String>> rows = ezeeClient.postForRoomRows(fields);

        if (dormitory == null || dormitory.isBlank()) {
            return rows;
        }
        String needle = dormitory.toLowerCase();
        return rows.stream()
                .filter(row -> row.get("roomtype") != null && row.get("roomtype").toLowerCase().contains(needle))
                .toList();
    }
```

Add imports `com.hostel.ordering.ezee.EzeeClient`, `java.util.LinkedHashMap`,
`java.util.Map` to `OrderService.java`.

Add the endpoint to `OrderController.java`:

```java
    @GetMapping("/{id}/ezee-candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, String>>> searchEzeeCandidates(
            @PathVariable String id,
            @RequestParam(required = false) String room,
            @RequestParam(required = false) String dormitory) {
        return ResponseEntity.ok(orderService.searchEzeeCandidates(room, dormitory));
    }
```

(`id` is unused by the lookup itself — kept in the path for REST consistency
with the order the admin is billing and to leave room for future
order-aware filtering; the search itself is a live eZee query, not scoped to
the order's stored data.)

- [ ] **Step 4: Run test, verify passes**
  Run: `mvn -q -Dtest=OrderServiceTest test`
  Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hostel/ordering/controller/OrderController.java src/main/java/com/hostel/ordering/service/OrderService.java src/test/java/com/hostel/ordering/service/OrderServiceTest.java
git commit -m "feat: add admin eZee occupant search endpoint"
```

---

### Task 6: `POST /orders/{id}/chargepost` and cancel-time auto-void

**Files:**
- Modify: `src/main/java/com/hostel/ordering/controller/OrderController.java`
- Modify: `src/main/java/com/hostel/ordering/service/OrderService.java`
- Modify: `src/test/java/com/hostel/ordering/service/OrderServiceTest.java`
  (existing constructor call gains two new arguments — see Step 1)

**Interfaces:**
- Consumes: `EzeeChargePostService.post(Order, String)`, `.voidPost(Order)`
  (Task 4).
- Produces: `OrderService.postChargeForOrder(String orderId, String room,
  String updatedBy) -> Order` — loads the order, calls
  `ezeeChargePostService.post(order, room)`, saves it; if the result's
  `chargePostStatus` is `"QUEUED"`, also sets `status = "CHECKED"` and
  saves again (single extra save, keeps the two concerns — chargepost outcome
  vs. order status — visibly separate in the audit log); returns the saved
  order either way (caller inspects `chargePostStatus`/`chargePostError` to
  know if it succeeded). Returns `null` if the order id doesn't exist.

- [ ] **Step 1: Write failing test**

The existing `setUp()` in `OrderServiceTest.java` calls
`new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository)`
— it must gain two more arguments (`EzeeChargePostService`, `EzeeClient`, in
that order) for the constructor's final shape:
`OrderService(orderRepository, fcmNotificationService, auditService, orderStatusService, menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient)`.
Update that line to
`new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository, null, null)`
— the existing `repriceOrder`-only tests never touch the new arguments, so
`null` is fine for them.

Add to `OrderServiceTest.java`:

```java
    @Mock
    com.hostel.ordering.ezee.EzeeChargePostService ezeeChargePostService;

    @Test
    void postChargeForOrder_ezeeAccepts_savesQueuedAndSetsChecked() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        existing.setBookingName("Test Guest");

        when(orderRepository.findById("order1")).thenReturn(java.util.Optional.of(existing));
        when(ezeeChargePostService.post(existing, "106")).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setChargePostStatus("QUEUED");
            o.setChargePostRequestId("2805");
            return o;
        });
        when(orderRepository.save(existing)).thenReturn(existing);

        Order result = svc.postChargeForOrder("order1", "106", "staff1");

        assertEquals("QUEUED", result.getChargePostStatus());
        assertEquals("CHECKED", result.getStatus());
        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.times(2)).save(existing);
    }

    @Test
    void postChargeForOrder_ezeeRejects_savesFailedAndLeavesStatusUnchanged() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        existing.setBookingName("Test Guest");

        when(orderRepository.findById("order1")).thenReturn(java.util.Optional.of(existing));
        when(ezeeChargePostService.post(existing, "106")).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setChargePostStatus("FAILED");
            o.setChargePostError("Room not occupied");
            return o;
        });
        when(orderRepository.save(existing)).thenReturn(existing);

        Order result = svc.postChargeForOrder("order1", "106", "staff1");

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("DELIVERED", result.getStatus());
        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.times(1)).save(existing);
    }

    @Test
    void postChargeForOrder_unknownOrder_returnsNull() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient);

        when(orderRepository.findById("missing")).thenReturn(java.util.Optional.empty());

        Order result = svc.postChargeForOrder("missing", "106", "staff1");

        assertNull(result);
    }

    @Test
    void updateOrderStatus_toCancelled_chargeQueued_voidsIt() {
        OrderService svc = new OrderService(orderRepository, fcmNotificationService, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("CHECKED");
        existing.setChargePostStatus("QUEUED");
        existing.setChargePostRequestId("2805");

        when(orderStatusService.getAllStatuses()).thenReturn(List.of(
                new com.hostel.ordering.model.OrderStatusConfig("CANCELLED", "Cancelled", "cancelled", false)));
        when(orderRepository.findById("order1")).thenReturn(java.util.Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        svc.updateOrderStatus("order1", "CANCELLED", "staff1");

        org.mockito.Mockito.verify(ezeeChargePostService).voidPost(existing);
    }

    @Test
    void updateOrderStatus_toCancelled_chargeNotQueued_doesNotCallVoid() {
        OrderService svc = new OrderService(orderRepository, fcmNotificationService, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");

        when(orderStatusService.getAllStatuses()).thenReturn(List.of(
                new com.hostel.ordering.model.OrderStatusConfig("CANCELLED", "Cancelled", "cancelled", false)));
        when(orderRepository.findById("order1")).thenReturn(java.util.Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        svc.updateOrderStatus("order1", "CANCELLED", "staff1");

        org.mockito.Mockito.verifyNoInteractions(ezeeChargePostService);
    }
```

- [ ] **Step 2: Run test, verify fails**
  Run: `mvn -q -Dtest=OrderServiceTest test`
  Expected: FAIL — `postChargeForOrder` does not exist / constructor arg
  count mismatch

- [ ] **Step 3: Implement**

Replace the `OrderService` constructor and field declarations with (this is
the combined final shape after Task 5 and this task):

```java
    private final OrderRepository orderRepository;
    private final FCMNotificationService fcmNotificationService;
    private final AuditService auditService;
    private final OrderStatusService orderStatusService;
    private final MenuItemRepository menuItemRepository;
    private final OtherEssentialRepository otherEssentialRepository;
    private final EzeeChargePostService ezeeChargePostService;
    private final EzeeClient ezeeClient;

    public OrderService(OrderRepository orderRepository,
                        FCMNotificationService fcmNotificationService,
                        AuditService auditService,
                        OrderStatusService orderStatusService,
                        MenuItemRepository menuItemRepository,
                        OtherEssentialRepository otherEssentialRepository,
                        EzeeChargePostService ezeeChargePostService,
                        EzeeClient ezeeClient) {
        this.orderRepository = orderRepository;
        this.fcmNotificationService = fcmNotificationService;
        this.auditService = auditService;
        this.orderStatusService = orderStatusService;
        this.menuItemRepository = menuItemRepository;
        this.otherEssentialRepository = otherEssentialRepository;
        this.ezeeChargePostService = ezeeChargePostService;
        this.ezeeClient = ezeeClient;
    }
```

Add the import `com.hostel.ordering.ezee.EzeeChargePostService` (alongside
the `EzeeClient` import added in Task 5).

Add the new method to `OrderService.java`:

```java
    public Order postChargeForOrder(String orderId, String room, String updatedBy) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return null;
        }

        Order result = ezeeChargePostService.post(order, room);
        if (updatedBy != null) {
            result.setUpdatedBy(updatedBy);
        }
        Order saved = orderRepository.save(result);

        if ("QUEUED".equals(saved.getChargePostStatus())) {
            saved.setStatus("CHECKED");
            saved.setUpdatedAt(System.currentTimeMillis());
            saved = orderRepository.save(saved);
            log.info("Order for {} posted to eZee and marked CHECKED", saved.getBookingName());
            auditService.logAction("ORDER_CHECKED", "Order for " + saved.getBookingName() + " posted to eZee room " + room + " and marked CHECKED");
        } else {
            log.warn("Chargepost failed for order {}, status unchanged: {}", saved.getId(), saved.getChargePostError());
            auditService.logAction("ORDER_CHARGEPOST_FAILED", "Chargepost failed for " + saved.getBookingName() + ": " + saved.getChargePostError());
        }

        return saved;
    }
```

Modify `updateOrderStatus()` to auto-void a `QUEUED` charge on cancel — add
this branch right after the existing `if ("CANCELLED".equalsIgnoreCase(status))`
FCM-notification call:

```java
                    if ("CANCELLED".equalsIgnoreCase(status)) {
                        fcmNotificationService.sendOrderCancelledNotification(updated);
                        if ("QUEUED".equals(updated.getChargePostStatus())) {
                            Order voided = ezeeChargePostService.voidPost(updated);
                            updated = orderRepository.save(voided);
                        }
                    }
```

Add the new endpoint to `OrderController.java`:

```java
    @PostMapping("/{id}/chargepost")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Order> postCharge(@PathVariable String id,
            @RequestBody Map<String, String> payload,
            Authentication authentication) {
        String updatedBy = authentication != null && authentication.isAuthenticated() ? authentication.getName() : "UNKNOWN";
        Order result = orderService.postChargeForOrder(id, payload.get("room"), updatedBy);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }
```

- [ ] **Step 4: Run test, verify passes**
  Run: `mvn -q -Dtest=OrderServiceTest test`
  Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hostel/ordering/controller/OrderController.java src/main/java/com/hostel/ordering/service/OrderService.java src/test/java/com/hostel/ordering/service/OrderServiceTest.java
git commit -m "feat: add admin chargepost endpoint and cancel-time auto-void"
```

---

### Task 7: Frontend API client — eZee search and chargepost calls

**Files:**
- Modify: `mosaichostels-cafe_frontend/js/api.js`

**Interfaces:**
- Produces:
  `API.orders.searchEzeeCandidates(orderId, { room, dormitory }) -> Promise<Array<Object>>`,
  `API.orders.chargepost(orderId, room) -> Promise<Object>` (the updated
  `Order`). Used by `admin.js`'s "Post & Check" modal (Task 8).

- [ ] **Step 1: Add the two methods**

Add inside the `orders` object in `api.js`, alongside `updateStatus` (no
test — this repo's frontend has no test suite; verified manually in Task 8):

```javascript
    searchEzeeCandidates: async function (orderId, params = {}) {
      const qs = new URLSearchParams();
      if (params.room) qs.set("room", params.room);
      if (params.dormitory) qs.set("dormitory", params.dormitory);
      const query = qs.toString() ? `?${qs.toString()}` : "";
      return await API.request(
        `/orders/${orderId}/ezee-candidates${query}`,
        {},
        "Failed to search eZee guests",
      );
    },

    chargepost: async function (orderId, room) {
      return await API.request(
        `/orders/${orderId}/chargepost`,
        {
          method: "POST",
          body: JSON.stringify({ room: room }),
        },
        "Failed to post charge to eZee",
      );
    },
```

- [ ] **Step 2: Commit**

```bash
cd /Users/naveen/Projects/hostel/mosaichostels-cafe_frontend
git add js/api.js
git commit -m "feat: add eZee search and chargepost API calls"
```

---

### Task 8: Admin UI — "Post & Check" modal

**Files:**
- Modify: `mosaichostels-cafe_frontend/admin.html`
- Modify: `mosaichostels-cafe_frontend/js/admin.js`

**Interfaces:**
- Consumes: `API.orders.searchEzeeCandidates`, `API.orders.chargepost`
  (Task 7).

- [ ] **Step 1: Add the modal markup**

Add to `admin.html`, right after the existing `confirmCancelModal` block
(same `.modal` / `.modal-content.card.elevation-3` / `.modal-header` /
`.modal-body` / `.modal-actions` structure used by the edit-item modal at
line 704):

```html
    <div id="postChargeModal" class="modal">
      <div class="modal-content card elevation-3">
        <div class="modal-header">
          <h3 class="modal-title">Post to eZee &amp; Check</h3>
          <button class="icon-button" onclick="AdminApp.closePostChargeModal()">
            <span class="material-icons">close</span>
          </button>
        </div>
        <div class="modal-body">
          <input type="hidden" id="postChargeOrderId" />
          <div class="text-field outlined">
            <input type="text" id="postChargeSearchInput" placeholder=" "
              oninput="AdminApp.searchEzeeCandidates()" />
            <label>Search by room number or dormitory</label>
            <div class="notch">
              <div class="notch-leading"></div>
              <div class="notch-middle"></div>
              <div class="notch-trailing"></div>
            </div>
          </div>
          <div id="postChargeResults"></div>
          <div id="postChargeError" style="color: var(--md-sys-color-error, #b3261e); display: none;"></div>
        </div>
        <div class="modal-actions">
          <button class="button text" onclick="AdminApp.closePostChargeModal()">
            Cancel
          </button>
        </div>
      </div>
    </div>
```

- [ ] **Step 2: Replace the "Mark as Checked" button**

In `admin.js` `displayOrders` (line ~611), replace:

```javascript
              (order.status || "").trim().toUpperCase() === "DELIVERED"
                ? `
                <button class="order-cancel-btn" onclick="AdminApp.markAsChecked('${order.id}')">
                    <span class="material-icons" style="font-size:16px">recommend</span>
                    Mark as Checked
                </button>
            `
                : ""
```

with:

```javascript
              (order.status || "").trim().toUpperCase() === "DELIVERED"
                ? `
                <button class="order-cancel-btn" onclick="AdminApp.openPostChargeModal('${order.id}')">
                    <span class="material-icons" style="font-size:16px">recommend</span>
                    Post &amp; Check
                </button>
            `
                : ""
```

- [ ] **Step 3: Add the modal's JS functions**

Add to `admin.js`, right after `markAsChecked` (which is now unused by the UI
and can be deleted — nothing else in the codebase calls it, confirmed by
searching `admin.js` for `markAsChecked` references):

```javascript
AdminApp.openPostChargeModal = function (orderId) {
  document.getElementById("postChargeOrderId").value = orderId;
  document.getElementById("postChargeSearchInput").value = "";
  document.getElementById("postChargeResults").innerHTML = "";
  document.getElementById("postChargeError").style.display = "none";
  document.getElementById("postChargeModal").classList.add("show");
};

AdminApp.closePostChargeModal = function () {
  document.getElementById("postChargeModal").classList.remove("show");
};

AdminApp.searchEzeeCandidates = async function () {
  const orderId = document.getElementById("postChargeOrderId").value;
  const query = document.getElementById("postChargeSearchInput").value.trim();
  const resultsEl = document.getElementById("postChargeResults");

  if (!query) {
    resultsEl.innerHTML = "";
    return;
  }

  // A bare number is treated as a room lookup (roomquery); anything else
  // searches the whole roomlist filtered by dormitory/room-type text.
  const isRoomNumber = /^\d+$/.test(query);
  const params = isRoomNumber ? { room: query } : { dormitory: query };

  try {
    const candidates = await API.orders.searchEzeeCandidates(orderId, params);
    if (candidates.length === 0) {
      resultsEl.innerHTML = `<p>No matching eZee guests found.</p>`;
      return;
    }
    resultsEl.innerHTML = candidates
      .map(
        (c) => `
        <div class="order-list-item" style="cursor:pointer" onclick="AdminApp.confirmPostCharge('${c.room}')">
            <strong>${UI.escapeHtml(c.guestname || "-")}</strong>
            — Room ${UI.escapeHtml(c.room || "-")}
        </div>
    `,
      )
      .join("");
  } catch (error) {
    resultsEl.innerHTML = `<p>Search failed. Please try again.</p>`;
  }
};

AdminApp.confirmPostCharge = async function (room) {
  const orderId = document.getElementById("postChargeOrderId").value;
  const errorEl = document.getElementById("postChargeError");
  errorEl.style.display = "none";

  try {
    const updated = await API.orders.chargepost(orderId, room);
    if (updated.chargePostStatus === "QUEUED") {
      this.closePostChargeModal();
      UI.showSuccess("Posted to eZee and marked checked");
      this.loadOrders();
    } else {
      errorEl.textContent = updated.chargePostError || "eZee rejected the charge. Pick a different room or try again.";
      errorEl.style.display = "block";
    }
  } catch (error) {
    errorEl.textContent = "Failed to post charge. Please try again.";
    errorEl.style.display = "block";
  }
};
```

- [ ] **Step 4: Manual verification**

With the backend running with `EZEE_MOCK=true`:

1. Deliver a cafe order (status `DELIVERED`) in the admin billing screen.
2. Click "Post & Check", type a room number (e.g. `106`) or dormitory text
   into the search box, confirm a mock candidate appears.
3. Click a candidate row — confirm the modal closes, a success snackbar
   shows, and the order now displays status `CHECKED`.
4. Cancel a `CHECKED` order that was just posted — confirm (via backend logs
   or a subsequent `GET /orders/{id}`) that `chargePostStatus` became
   `VOIDED`.

- [ ] **Step 5: Commit**

```bash
cd /Users/naveen/Projects/hostel/mosaichostels-cafe_frontend
git add admin.html js/admin.js
git commit -m "feat: add Post & Check modal for admin-mapped eZee posting"
```
