# eZee Chargepost Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** As soon as a guest or staff places a cafe order (`POST /orders`,
shared by the guest website and the Android app), automatically post the
charge to the guest's eZee PMS folio — no staff step required. `CHECKED`-time
posting becomes a retry-only safety net, and cancelling an order that already
posted automatically voids the charge.

**Architecture:** A new `ezee` package with three collaborators, each with one
job: `EzeeClient` (HTTP + XML transport to the `service.pos2pms` endpoint),
`EzeeRoomResolver` (figures out which live eZee `room`/`folio` an order's
guest is actually in), `EzeeChargePostService` (orchestrates resolve → post,
writes the outcome onto the `Order` document, and can also void a posted
charge). The primary trigger is `OrderService.createOrder()`, which fires
`EzeeChargePostService.postAsync(order)` — an `@Async` method (Spring's async
executor is already enabled via `@EnableAsync` on
`HostelOrderingApplication`) — so the guest-facing `POST /orders` response
never waits on eZee. `OrderService.updateOrderStatus()` keeps a synchronous
retry: if status moves to `CHECKED` and no successful post happened yet
(`chargePostStatus` is `null` or `"FAILED"`), it calls `post()` again. If
status moves to `CANCELLED` on an order whose charge is `QUEUED`, it calls
`voidPost()` to reverse it. No path ever lets a chargepost failure block an
order or status update — chargepost is best-effort automation layered on a
manual eZee-entry process that still works as the fallback.

**Tech Stack:** Spring Boot 3.5.10, Java 17, Spring Data MongoDB, JUnit 5 +
Mockito (existing test stack). No new Maven dependencies — HTTP via
`java.net.http.HttpClient`, XML via `javax.xml.parsers`/`javax.xml.transform`
(all JDK standard library).

**Related docs:**
- `Website/docs/ezee-charge-posting-integration.md` — original analysis (this
  plan supersedes its "Open gap" and "Not yet decided / async failure"
  sections with the decisions below).
- `Website/docs/eZee-Connectivity-API.md` lines 4061–4406 — authoritative
  `chargepost`/`voidcharge`/`updatevoucherno`/`roomlist`/`roomquery` spec.

## Decisions locked in (supersedes the open items in the original doc)

1. **Room/folio resolution is always live, never fully static.** `folio` is a
   per-stay ledger number assigned by eZee at check-in — it cannot be
   hardcoded even for the two private-room dormitories. What *can* be static
   is which eZee **room number** or **room type string** a dormitory
   corresponds to:
   - Private-room dormitories (`101 - Private Room`, `201 - Private Room`) get
     a configured `ezeeRoomNumber` → resolved via `roomquery(room)` at
     posting time (cheap, single-room lookup, and doubles as existence proof —
     `roomquery` fails outright if the room isn't currently occupied).
   - Shared-dorm dormitories get a configured `ezeeRoomType` string (the
     value eZee's `roomtype` field returns) → resolved via `roomlist()`,
     filtered to matching `roomtype` rows, then matched by guest name.
   - Either path ends with a **guest-name sanity check** against
     `order.bookingName` (normalized, case/whitespace-insensitive
     contains-match). Exactly one candidate row required; 0 or 2+ candidates
     is a resolution failure, never a guess — posting to the wrong guest's
     folio is the one failure mode worse than not posting at all.
2. **Async failure handling: no polling exists, so don't build one.** The
   full `chargepost` spec was read end to end — there is no status-check or
   webhook API for a queued `requestid`, only `voidcharge`/`updatevoucherno`
   which require staff to already know something's wrong. But the error
   catalogue (folio not found, credit limit, tax mapping mismatch, POS2PMS
   not set up, bad auth) all return as an **immediate synchronous**
   `status=error` in the chargepost response — only genuine PMS-side ledger
   application is deferred behind "added in queue". So: treat the synchronous
   response as authoritative, record it on the `Order`, and accept the small
   residual risk of a `status=ok` request that still fails deep in the PMS
   (unrecoverable via any API eZee exposes — `voidcharge` is there if staff
   spot it manually later).
3. **State lives on the `Order` document, not a filesystem queue.** The
   sibling `Website/ezee-pending-orders` filesystem queue exists because PHP
   booking flows had no natural document to attach status to. This backend
   already has a Mongo `Order` document per request — five new fields on it
   are simpler and more consistent with the rest of this codebase than
   importing a filesystem-queue pattern that doesn't fit the stack.
4. **No tax split.** `MenuItem`/`OrderItem`/`Order` have no tax field
   anywhere in this codebase — cafe pricing is flat. `chargepost` requires
   `amount` (excl. tax) and `gross_amount` (incl. tax); send
   `amount = gross_amount = order.totalAmount`, `tax = 0.00`. Don't invent
   tax logic the domain doesn't have.
5. **Chargepost never blocks the `CHECKED` status update.** `OrderService`
   catches everything from the orchestrator, logs it, writes a `FAILED`
   outcome onto the order, and still returns the updated (now-`CHECKED`)
   order. The manual eZee entry staff already do today remains the fallback
   for any failure.
6. **Primary trigger moves to order creation; `CHECKED` becomes a retry
   safety net.** Posting at creation time means the request is
   guest-initiated and unauthenticated (`POST /orders` is `permitAll()`), so
   it must never add latency or a failure path to that endpoint — dispatch
   via `EzeeChargePostService.postAsync(Order)`, an `@Async` void method that
   posts and saves the result itself. `OrderService.createOrder()` still
   saves and returns the order synchronously before the async post finishes;
   `postAsync` does its own `orderRepository.save()` once the eZee call
   returns. The `CHECKED`-time call in `updateOrderStatus()` stays, but only
   fires when nothing has succeeded yet (`chargePostStatus == null ||
   "FAILED".equals(chargePostStatus)`) — it's now a manual-billing-time
   catch-up for orders where the async post never ran or failed.
7. **Cancelling a `QUEUED` order must void it.** If an order's charge already
   posted (`chargePostStatus == "QUEUED"`) and staff then cancel the order,
   the guest's folio would carry a stale real charge with nothing to reverse
   it. `updateOrderStatus()` calls `EzeeChargePostService.voidPost(Order)`
   when transitioning to `CANCELLED` on a `QUEUED` order — reuses the
   existing generic `EzeeClient.post()` transport with `oprn=voidcharge` and
   `requestid=order.getChargePostRequestId()` (no `EzeeClient` change
   needed). On success, `chargePostStatus` becomes `"VOIDED"`; on failure,
   `chargePostStatus` stays `"QUEUED"` (the charge is still live — voiding
   didn't work, so the state must say so, not paper over it) and
   `chargePostError` records why, for staff to void by hand.

## Global Constraints

- No new Maven dependencies.
- No `/api` prefix on routes (existing convention).
- Config via `@Value("${ezee.xxx:default}")` reading env vars, matching the
  existing `config.jwtSecret` / `cors.allowed-origins` pattern in
  `application-prod.yml`.
- A `EZEE_MOCK=true` env flag must short-circuit `EzeeClient` to canned
  in-memory responses, mirroring `Website/api/lib/mock.php`'s
  `EZEE_MOCK_ROOMLIST` convenience — lets this be developed/tested without
  live eZee credentials.
- `chargepost`/`roomlist`/`roomquery` request and response bodies are XML —
  build and parse via JDK `javax.xml.parsers.DocumentBuilder` /
  `javax.xml.transform.Transformer`, never string concatenation (guest names
  and menu item names are free text and must be XML-escaped).

---

### Task 1: Dormitory eZee mapping fields

**Files:**
- Modify: `src/main/java/com/hostel/ordering/model/Dormitory.java`
- Test: `src/test/java/com/hostel/ordering/model/DormitoryTest.java`

**Interfaces:**
- Produces: `Dormitory.getEzeeRoomNumber()`, `Dormitory.getEzeeRoomType()` —
  both nullable `String`, both used by `EzeeRoomResolver` in Task 6.

- [ ] **Step 1: Write failing test**

```java
package com.hostel.ordering.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DormitoryTest {
    @Test
    void ezeeMappingFields_defaultToNull_andAreSettable() {
        Dormitory d = new Dormitory("101 - Private Room");
        assertNull(d.getEzeeRoomNumber());
        assertNull(d.getEzeeRoomType());

        d.setEzeeRoomNumber("101");
        d.setEzeeRoomType("Delux");

        assertEquals("101", d.getEzeeRoomNumber());
        assertEquals("Delux", d.getEzeeRoomType());
    }
}
```

- [ ] **Step 2: Run test, verify fails**
  Run: `mvn -q -Dtest=DormitoryTest test`
  Expected: FAIL — `getEzeeRoomNumber` does not exist

- [ ] **Step 3: Add the fields**

```java
package com.hostel.ordering.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document(collection = "dormitories")
public class Dormitory {

    @Id
    private String id;
    private String name;

    // eZee POS Connectivity mapping — set by an admin, both nullable until configured.
    // Private-room dormitories set ezeeRoomNumber (resolved via roomquery).
    // Shared-dorm dormitories set ezeeRoomType (resolved via roomlist + guest-name match).
    private String ezeeRoomNumber;
    private String ezeeRoomType;

    public Dormitory() {
    }

    public Dormitory(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEzeeRoomNumber() {
        return ezeeRoomNumber;
    }

    public void setEzeeRoomNumber(String ezeeRoomNumber) {
        this.ezeeRoomNumber = ezeeRoomNumber;
    }

    public String getEzeeRoomType() {
        return ezeeRoomType;
    }

    public void setEzeeRoomType(String ezeeRoomType) {
        this.ezeeRoomType = ezeeRoomType;
    }
}
```

- [ ] **Step 4: Run test, verify passes**
  Run: `mvn -q -Dtest=DormitoryTest test`
  Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hostel/ordering/model/Dormitory.java src/test/java/com/hostel/ordering/model/DormitoryTest.java
git commit -m "feat: add eZee room mapping fields to Dormitory"
```

---

### Task 2: Order chargepost outcome fields

**Files:**
- Modify: `src/main/java/com/hostel/ordering/model/Order.java`
- Test: `src/test/java/com/hostel/ordering/model/OrderTest.java`

**Interfaces:**
- Produces: `Order.getChargePostStatus()/setChargePostStatus(String)` (null |
  `"QUEUED"` | `"FAILED"` | `"VOIDED"`), `getChargePostRequestId()/set...(String)`,
  `getChargePostError()/set...(String)`, `getChargePostRoom()/set...(String)`,
  `getChargePostFolio()/set...(String)`, `getChargePostAt()/set...(Long)` —
  all used by `EzeeChargePostService` in Task 8 and `OrderService` in Task 9.

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

```java
package com.hostel.ordering.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Document(collection = "orders")
public class Order {
    @Id
    private String id;

    @NotBlank(message = "Booking name is required")
    private String bookingName;

    @NotBlank(message = "Dormitory is required")
    private String dormitory;

    @NotEmpty(message = "Items list cannot be empty")
    private List<OrderItem> items;

    private Double totalAmount;
    private String status;
    private String createdBy;
    private String updatedBy;
    private Long createdAt;
    private Long updatedAt;

    // eZee chargepost outcome — set on order creation (async), retried at
    // CHECKED if still unset/failed, reversed to VOIDED on cancel.
    // Null status = never attempted. "QUEUED" = eZee accepted it (status=ok).
    // "FAILED" = resolution or eZee rejected it (see chargePostError).
    // "VOIDED" = order was cancelled after posting and the charge was reversed.
    private String chargePostStatus;
    private String chargePostRequestId;
    private String chargePostError;
    private String chargePostRoom;
    private String chargePostFolio;
    private Long chargePostAt;

    public Order() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBookingName() { return bookingName; }
    public void setBookingName(String bookingName) { this.bookingName = bookingName; }

    public String getDormitory() { return dormitory; }
    public void setDormitory(String dormitory) { this.dormitory = dormitory; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }

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
}
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

### Task 3: `EzeeXmlUtil` — build request XML / parse response XML

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

### Task 4: `EzeeClient` — HTTP transport + mock mode

**Files:**
- Create: `src/main/java/com/hostel/ordering/ezee/EzeeClient.java`
- Test: `src/test/java/com/hostel/ordering/ezee/EzeeClientTest.java`

**Interfaces:**
- Consumes: `EzeeXmlUtil.buildRequest`/`parseFlatResponse` (Task 3).
- Produces: `EzeeClient.post(LinkedHashMap<String,String> fields) -> Map<String,String>`
  — posts to the configured endpoint (or returns a mock map when
  `ezee.mock=true`), used by `EzeeRoomResolver` (Task 6) and
  `EzeeChargePostService` (Task 8) for every `oprn` (`roomlist`, `roomquery`,
  `chargepost`, `voidcharge`).

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
// into an HTTP POST and the response body into a field map. Callers (roomlist
// lookups, chargepost) live in EzeeRoomResolver/EzeeChargePostService.
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

// Canned responses for local/dev use when ezee.mock=true — mirrors
// Website/api/lib/mock.php's EZEE_MOCK_ROOMLIST convenience so this
// integration can be developed without live eZee credentials.
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
        Map<String, String> row = new LinkedHashMap<>();
        row.put("guestname", "Mock Guest");
        row.put("room", "106");
        row.put("masterfolio", "10");
        row.put("roomtype", "8 - Bed Mixed Dorm");
        rows.add(row);
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

### Task 5: `EzeeRoomResolver` — resolve room/folio for an order

**Files:**
- Create: `src/main/java/com/hostel/ordering/ezee/EzeeRoomResolver.java`
- Create: `src/main/java/com/hostel/ordering/ezee/RoomResolution.java`
- Test: `src/test/java/com/hostel/ordering/ezee/EzeeRoomResolverTest.java`

**Interfaces:**
- Consumes: `EzeeClient.post`/`postForRoomRows` (Task 4),
  `DormitoryRepository.findAll()` (existing), `Order.getDormitory()`/`getBookingName()`.
- Produces: `EzeeRoomResolver.resolve(Order order) -> RoomResolution` where
  `RoomResolution` is a small record: `RoomResolution.success(String room, String folio)`
  / `RoomResolution.failure(String reason)`, with `isSuccess()`, `getRoom()`,
  `getFolio()`, `getFailureReason()`. Used by `EzeeChargePostService` (Task 8).

- [ ] **Step 1: Write failing test**

```java
package com.hostel.ordering.ezee;

import com.hostel.ordering.model.Dormitory;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.repository.DormitoryRepository;
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
class EzeeRoomResolverTest {

    @Mock
    DormitoryRepository dormitoryRepository;

    @Mock
    EzeeClient ezeeClient;

    private EzeeRoomResolver resolver;

    private Order order(String bookingName, String dormitory) {
        Order o = new Order();
        o.setBookingName(bookingName);
        o.setDormitory(dormitory);
        return o;
    }

    private Dormitory dormitory(String name, String roomNumber, String roomType) {
        Dormitory d = new Dormitory(name);
        d.setEzeeRoomNumber(roomNumber);
        d.setEzeeRoomType(roomType);
        return d;
    }

    @Test
    void resolve_privateRoom_usesRoomqueryAndVerifiesGuestName() {
        resolver = new EzeeRoomResolver(dormitoryRepository, ezeeClient);
        when(dormitoryRepository.findAll()).thenReturn(
                List.of(dormitory("101 - Private Room", "101", null)));

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        roomqueryResponse.put("guestname", "Mr. Denial Mark");
        roomqueryResponse.put("masterfolio", "8");
        when(ezeeClient.post(argWithOprn("roomquery"))).thenReturn(roomqueryResponse);

        RoomResolution result = resolver.resolve(order("Denial Mark", "101 - Private Room"));

        assertTrue(result.isSuccess());
        assertEquals("101", result.getRoom());
        assertEquals("8", result.getFolio());
    }

    @Test
    void resolve_privateRoom_guestNameMismatch_fails() {
        resolver = new EzeeRoomResolver(dormitoryRepository, ezeeClient);
        when(dormitoryRepository.findAll()).thenReturn(
                List.of(dormitory("101 - Private Room", "101", null)));

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        roomqueryResponse.put("guestname", "Someone Else Entirely");
        roomqueryResponse.put("masterfolio", "8");
        when(ezeeClient.post(argWithOprn("roomquery"))).thenReturn(roomqueryResponse);

        RoomResolution result = resolver.resolve(order("Denial Mark", "101 - Private Room"));

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureReason().toLowerCase().contains("name"));
    }

    @Test
    void resolve_sharedDorm_matchesSingleRowByRoomTypeAndGuestName() {
        resolver = new EzeeRoomResolver(dormitoryRepository, ezeeClient);
        when(dormitoryRepository.findAll()).thenReturn(
                List.of(dormitory("8 - Bed Mixed Dorm", null, "Studio")));

        Map<String, String> row1 = new LinkedHashMap<>();
        row1.put("guestname", "Mr. Joy");
        row1.put("room", "106");
        row1.put("masterfolio", "10");
        row1.put("roomtype", "Studio");

        Map<String, String> row2 = new LinkedHashMap<>();
        row2.put("guestname", "Mrs Sophia");
        row2.put("room", "109");
        row2.put("masterfolio", "22");
        row2.put("roomtype", "Single Bedroom Suite");

        when(ezeeClient.postForRoomRows(argWithOprn("roomlist"))).thenReturn(List.of(row1, row2));

        RoomResolution result = resolver.resolve(order("joy", "8 - Bed Mixed Dorm"));

        assertTrue(result.isSuccess());
        assertEquals("106", result.getRoom());
        assertEquals("10", result.getFolio());
    }

    @Test
    void resolve_sharedDorm_ambiguousMatch_fails() {
        resolver = new EzeeRoomResolver(dormitoryRepository, ezeeClient);
        when(dormitoryRepository.findAll()).thenReturn(
                List.of(dormitory("8 - Bed Mixed Dorm", null, "Studio")));

        Map<String, String> row1 = new LinkedHashMap<>();
        row1.put("guestname", "John Smith");
        row1.put("room", "106");
        row1.put("masterfolio", "10");
        row1.put("roomtype", "Studio");

        Map<String, String> row2 = new LinkedHashMap<>();
        row2.put("guestname", "John Smithers");
        row2.put("room", "107");
        row2.put("masterfolio", "11");
        row2.put("roomtype", "Studio");

        when(ezeeClient.postForRoomRows(argWithOprn("roomlist"))).thenReturn(List.of(row1, row2));

        RoomResolution result = resolver.resolve(order("John Smith", "8 - Bed Mixed Dorm"));

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureReason().toLowerCase().contains("ambiguous"));
    }

    @Test
    void resolve_dormitoryNotConfigured_fails() {
        resolver = new EzeeRoomResolver(dormitoryRepository, ezeeClient);
        when(dormitoryRepository.findAll()).thenReturn(
                List.of(dormitory("6 - Bed Female Dorm", null, null)));

        RoomResolution result = resolver.resolve(order("Anyone", "6 - Bed Female Dorm"));

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureReason().toLowerCase().contains("not configured"));
    }

    private LinkedHashMap<String, String> argWithOprn(String oprn) {
        return org.mockito.ArgumentMatchers.argThat(m -> m != null && oprn.equals(m.get("oprn")));
    }
}
```

- [ ] **Step 2: Run test, verify fails**
  Run: `mvn -q -Dtest=EzeeRoomResolverTest test`
  Expected: FAIL — `EzeeRoomResolver`/`RoomResolution` do not exist

- [ ] **Step 3: Implement**

```java
package com.hostel.ordering.ezee;

public final class RoomResolution {
    private final boolean success;
    private final String room;
    private final String folio;
    private final String failureReason;

    private RoomResolution(boolean success, String room, String folio, String failureReason) {
        this.success = success;
        this.room = room;
        this.folio = folio;
        this.failureReason = failureReason;
    }

    public static RoomResolution success(String room, String folio) {
        return new RoomResolution(true, room, folio, null);
    }

    public static RoomResolution failure(String reason) {
        return new RoomResolution(false, null, null, reason);
    }

    public boolean isSuccess() { return success; }
    public String getRoom() { return room; }
    public String getFolio() { return folio; }
    public String getFailureReason() { return failureReason; }
}
```

```java
package com.hostel.ordering.ezee;

import com.hostel.ordering.model.Dormitory;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.repository.DormitoryRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Resolves which live eZee room/folio an order's guest is currently in.
// Private-room dormitories: roomquery(configured room number) + guest-name check.
// Shared-dorm dormitories: roomlist() filtered by configured room type, then
// exactly-one guest-name match required.
@Service
public class EzeeRoomResolver {

    private final DormitoryRepository dormitoryRepository;
    private final EzeeClient ezeeClient;

    public EzeeRoomResolver(DormitoryRepository dormitoryRepository, EzeeClient ezeeClient) {
        this.dormitoryRepository = dormitoryRepository;
        this.ezeeClient = ezeeClient;
    }

    public RoomResolution resolve(Order order) {
        Dormitory dormitory = dormitoryRepository.findAll().stream()
                .filter(d -> d.getName().equals(order.getDormitory()))
                .findFirst()
                .orElse(null);

        if (dormitory == null) {
            return RoomResolution.failure("Unknown dormitory: " + order.getDormitory());
        }

        if (dormitory.getEzeeRoomNumber() != null && !dormitory.getEzeeRoomNumber().isBlank()) {
            return resolveByRoomNumber(dormitory.getEzeeRoomNumber(), order.getBookingName());
        }

        if (dormitory.getEzeeRoomType() != null && !dormitory.getEzeeRoomType().isBlank()) {
            return resolveByRoomType(dormitory.getEzeeRoomType(), order.getBookingName());
        }

        return RoomResolution.failure("Dormitory not configured for eZee charge posting: " + dormitory.getName());
    }

    private RoomResolution resolveByRoomNumber(String roomNumber, String bookingName) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("auth", ezeeClient.getAuthCode());
        fields.put("oprn", "roomquery");
        fields.put("room", roomNumber);

        Map<String, String> response = ezeeClient.post(fields);
        if (!"ok".equals(response.get("status"))) {
            return RoomResolution.failure("roomquery failed: " + response.getOrDefault("msg", "unknown error"));
        }

        String guestName = response.get("guestname");
        if (!namesMatch(bookingName, guestName)) {
            return RoomResolution.failure(
                    "Guest name mismatch on room " + roomNumber + ": order has '" + bookingName
                            + "', eZee has '" + guestName + "'");
        }

        return RoomResolution.success(roomNumber, response.get("masterfolio"));
    }

    private RoomResolution resolveByRoomType(String roomType, String bookingName) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("auth", ezeeClient.getAuthCode());
        fields.put("oprn", "roomlist");

        List<Map<String, String>> rows = ezeeClient.postForRoomRows(fields);

        List<Map<String, String>> matches = rows.stream()
                .filter(row -> roomType.equalsIgnoreCase(row.get("roomtype")))
                .filter(row -> namesMatch(bookingName, row.get("guestname")))
                .toList();

        if (matches.isEmpty()) {
            return RoomResolution.failure("No in-house guest matching '" + bookingName + "' found in room type " + roomType);
        }
        if (matches.size() > 1) {
            return RoomResolution.failure(
                    matches.size() + " ambiguous matches for '" + bookingName + "' in room type " + roomType);
        }

        Map<String, String> match = matches.get(0);
        return RoomResolution.success(match.get("room"), match.get("masterfolio"));
    }

    // Case/whitespace-insensitive, either name containing the other counts as a match —
    // order.bookingName is free text a guest typed, not validated against the real reservation.
    private boolean namesMatch(String bookingName, String guestName) {
        if (bookingName == null || guestName == null) return false;
        String a = normalize(bookingName);
        String b = normalize(guestName);
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.contains(b) || b.contains(a);
    }

    private String normalize(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
```

- [ ] **Step 4: Run test, verify passes**
  Run: `mvn -q -Dtest=EzeeRoomResolverTest test`
  Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hostel/ordering/ezee/EzeeRoomResolver.java src/main/java/com/hostel/ordering/ezee/RoomResolution.java src/test/java/com/hostel/ordering/ezee/EzeeRoomResolverTest.java
git commit -m "feat: add EzeeRoomResolver for order-to-folio resolution"
```

---

### Task 6: `EzeeChargePostService` — orchestrator

**Files:**
- Create: `src/main/java/com/hostel/ordering/ezee/EzeeChargePostService.java`
- Test: `src/test/java/com/hostel/ordering/ezee/EzeeChargePostServiceTest.java`

**Interfaces:**
- Consumes: `EzeeRoomResolver.resolve` (Task 5), `EzeeClient.post` (Task 4),
  `Order` chargepost fields (Task 2), `OrderRepository.save` (existing).
- Produces:
  - `EzeeChargePostService.post(Order order) -> Order` — mutates and returns
    the same `Order` instance with `chargePost*` fields set (does NOT save —
    the caller owns persistence). Used by `OrderService.updateOrderStatus()`'s
    `CHECKED`-time retry (Task 7).
  - `EzeeChargePostService.postAsync(Order order) -> void` — `@Async`, calls
    `post(order)` then saves the result itself via `orderRepository.save()`
    (fire-and-forget — no caller waits on it). Used by
    `OrderService.createOrder()` (Task 7).
  - `EzeeChargePostService.voidPost(Order order) -> Order` — sends
    `oprn=voidcharge` with `order.getChargePostRequestId()`, mutates and
    returns the same `Order` (does NOT save). Sets `chargePostStatus="VOIDED"`
    on success; on failure leaves `chargePostStatus` untouched (still
    `"QUEUED"`) and records the reason in `chargePostError`. Used by
    `OrderService.updateOrderStatus()`'s `CANCELLED`-time branch (Task 7).
  - All three never throw — every failure path is captured into
    `chargePostStatus`/`chargePostError`, never propagated to the caller.

- [ ] **Step 1: Write failing test**

```java
package com.hostel.ordering.ezee;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import com.hostel.ordering.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EzeeChargePostServiceTest {

    @Mock
    EzeeRoomResolver roomResolver;

    @Mock
    EzeeClient ezeeClient;

    @Mock
    OrderRepository orderRepository;

    private EzeeChargePostService service;

    private Order sampleOrder() {
        Order order = new Order();
        order.setId("order123");
        order.setBookingName("Denial Mark");
        order.setDormitory("101 - Private Room");
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
    void post_resolutionSucceeds_chargepostSucceeds_marksQueued() {
        service = new EzeeChargePostService(roomResolver, ezeeClient, orderRepository, "Cafe");
        when(roomResolver.resolve(any())).thenReturn(RoomResolution.success("101", "8"));

        Map<String, String> chargepostResponse = new LinkedHashMap<>();
        chargepostResponse.put("status", "ok");
        chargepostResponse.put("msg", "added in queue");
        chargepostResponse.put("requestid", "2805");
        when(ezeeClient.post(argWithOprn("chargepost"))).thenReturn(chargepostResponse);

        Order result = service.post(sampleOrder());

        assertEquals("QUEUED", result.getChargePostStatus());
        assertEquals("2805", result.getChargePostRequestId());
        assertEquals("101", result.getChargePostRoom());
        assertEquals("8", result.getChargePostFolio());
        assertNull(result.getChargePostError());
        assertNotNull(result.getChargePostAt());
    }

    @Test
    void post_resolutionFails_marksFailedWithoutCallingEzee() {
        service = new EzeeChargePostService(roomResolver, ezeeClient, orderRepository, "Cafe");
        when(roomResolver.resolve(any())).thenReturn(RoomResolution.failure("Dormitory not configured"));

        Order result = service.post(sampleOrder());

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("Dormitory not configured", result.getChargePostError());
        assertNull(result.getChargePostRequestId());
    }

    @Test
    void post_ezeeReturnsError_marksFailed() {
        service = new EzeeChargePostService(roomResolver, ezeeClient, orderRepository, "Cafe");
        when(roomResolver.resolve(any())).thenReturn(RoomResolution.success("101", "8"));

        Map<String, String> chargepostResponse = new LinkedHashMap<>();
        chargepostResponse.put("status", "error");
        chargepostResponse.put("msg", "Folio not found in PMS");
        when(ezeeClient.post(argWithOprn("chargepost"))).thenReturn(chargepostResponse);

        Order result = service.post(sampleOrder());

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("Folio not found in PMS", result.getChargePostError());
    }

    @Test
    void postAsync_resolvesAndPosts_savesResultToRepository() {
        service = new EzeeChargePostService(roomResolver, ezeeClient, orderRepository, "Cafe");
        when(roomResolver.resolve(any())).thenReturn(RoomResolution.success("101", "8"));

        Map<String, String> chargepostResponse = new LinkedHashMap<>();
        chargepostResponse.put("status", "ok");
        chargepostResponse.put("requestid", "2805");
        when(ezeeClient.post(argWithOprn("chargepost"))).thenReturn(chargepostResponse);

        Order order = sampleOrder();
        service.postAsync(order);

        assertEquals("QUEUED", order.getChargePostStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void voidPost_ezeeConfirms_marksVoided() {
        service = new EzeeChargePostService(roomResolver, ezeeClient, orderRepository, "Cafe");
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
        service = new EzeeChargePostService(roomResolver, ezeeClient, orderRepository, "Cafe");
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

    private org.mockito.stubbing.Answer<Object> any() {
        return invocation -> RoomResolution.failure("unused");
    }

    private LinkedHashMap<String, String> argWithOprn(String oprn) {
        return org.mockito.ArgumentMatchers.argThat(m -> m != null && oprn.equals(m.get("oprn")));
    }
}
```

**Note for implementer:** the `any()` helper above is a placeholder that
doesn't compile as written (Mockito's `any()` matcher, not an `Answer`) — use
`org.mockito.ArgumentMatchers.any(Order.class)` in the `when(roomResolver.resolve(...))`
stubs instead, e.g. `when(roomResolver.resolve(any(Order.class))).thenReturn(...)`
with `import static org.mockito.ArgumentMatchers.any;`. Fix this import/call
when writing the test file for real — this is flagged explicitly so it isn't
copied verbatim.

- [ ] **Step 2: Run test, verify fails**
  Run: `mvn -q -Dtest=EzeeChargePostServiceTest test`
  Expected: FAIL — `EzeeChargePostService` does not exist

- [ ] **Step 3: Implement**

```java
package com.hostel.ordering.ezee;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import com.hostel.ordering.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Orchestrates resolve-then-post for a single order's chargepost. Mutates and
// returns the same Order with chargePost* fields set; never throws — every
// failure path lands in chargePostStatus=FAILED so the caller's status
// update is never blocked by this integration.
@Service
public class EzeeChargePostService {

    private static final Logger log = LoggerFactory.getLogger(EzeeChargePostService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EzeeRoomResolver roomResolver;
    private final EzeeClient ezeeClient;
    private final OrderRepository orderRepository;
    private final String outlet;

    public EzeeChargePostService(
            EzeeRoomResolver roomResolver,
            EzeeClient ezeeClient,
            OrderRepository orderRepository,
            @Value("${ezee.outlet:Cafe}") String outlet) {
        this.roomResolver = roomResolver;
        this.ezeeClient = ezeeClient;
        this.orderRepository = orderRepository;
        this.outlet = outlet;
    }

    // Fire-and-forget entry point for OrderService.createOrder() — the guest-
    // facing POST /orders response must not wait on eZee. Saves the outcome
    // itself since no synchronous caller will persist it.
    @Async
    public void postAsync(Order order) {
        post(order);
        orderRepository.save(order);
    }

    public Order post(Order order) {
        order.setChargePostAt(System.currentTimeMillis());
        try {
            RoomResolution resolution = roomResolver.resolve(order);
            if (!resolution.isSuccess()) {
                return markFailed(order, resolution.getFailureReason());
            }

            LinkedHashMap<String, String> fields = buildChargePostFields(order, resolution);
            Map<String, String> response = ezeeClient.post(fields);

            if ("ok".equals(response.get("status"))) {
                order.setChargePostStatus("QUEUED");
                order.setChargePostRequestId(response.get("requestid"));
                order.setChargePostRoom(resolution.getRoom());
                order.setChargePostFolio(resolution.getFolio());
                order.setChargePostError(null);
                log.info("Chargepost queued for order {}: requestid={}", order.getId(), response.get("requestid"));
                return order;
            }

            order.setChargePostRoom(resolution.getRoom());
            order.setChargePostFolio(resolution.getFolio());
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

    private LinkedHashMap<String, String> buildChargePostFields(Order order, RoomResolution resolution) {
        String today = LocalDate.now().format(DATE_FORMAT);
        String remark = order.getItems().stream()
                .map(OrderItem::getMenuItemName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
        String amount = String.format("%.2f", order.getTotalAmount());

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("auth", ezeeClient.getAuthCode());
        fields.put("oprn", "chargepost");
        fields.put("room", resolution.getRoom());
        fields.put("folio", resolution.getFolio());
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

- [ ] **Step 4: Fix the test's `any()` matcher per the implementer note in
  Step 1, then run and verify passes**
  Run: `mvn -q -Dtest=EzeeChargePostServiceTest test`
  Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hostel/ordering/ezee/EzeeChargePostService.java src/test/java/com/hostel/ordering/ezee/EzeeChargePostServiceTest.java
git commit -m "feat: add EzeeChargePostService orchestrator"
```

---

### Task 7: Wire into `OrderService.createOrder()` and `updateOrderStatus()`

**Files:**
- Modify: `src/main/java/com/hostel/ordering/service/OrderService.java`
- Modify: `src/test/java/com/hostel/ordering/service/OrderServiceTest.java`
  (existing constructor call gains a new argument — see Step 1)

**Interfaces:**
- Consumes: `EzeeChargePostService.post(Order)`, `.postAsync(Order)`,
  `.voidPost(Order)` (Task 6).

- [ ] **Step 1: Write failing test**

Add to `OrderServiceTest.java`. The existing `setUp()` calls
`new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository)`
— it must gain a 7th argument for the new dependency. Since the existing
`repriceOrder` tests never touch `createOrder`/`updateOrderStatus`, passing
`null` there is fine for them; the new tests below construct their own
`OrderService` instance with the dependency mocked.

```java
    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderStatusService orderStatusService;

    @Mock
    AuditService auditService;

    @Mock
    FCMNotificationService fcmNotificationService;

    @Mock
    com.hostel.ordering.ezee.EzeeChargePostService ezeeChargePostService;

    @Test
    void createOrder_savesOrder_thenDispatchesChargePostAsync() {
        OrderService svc = new OrderService(orderRepository, fcmNotificationService, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService);

        Order order = new Order();
        order.setBookingName("Test Guest");
        order.setDormitory("101 - Private Room");
        OrderItem item = new OrderItem();
        item.setMenuItemId("item1");
        item.setQuantity(1);
        order.setItems(List.of(item));

        com.hostel.ordering.model.MenuItem menuItem = new com.hostel.ordering.model.MenuItem();
        menuItem.setPrice(20.0);
        when(menuItemRepository.findById("item1")).thenReturn(Optional.of(menuItem));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = svc.createOrder(order);

        org.mockito.Mockito.verify(ezeeChargePostService).postAsync(result);
    }

    @Test
    void updateOrderStatus_toChecked_neverAttempted_callsChargePostServiceAndPersistsResult() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        existing.setBookingName("Test Guest");

        when(orderStatusService.getAllStatuses()).thenReturn(List.of(
                new com.hostel.ordering.model.OrderStatusConfig("CHECKED", "Checked", "checked", false)));
        when(orderRepository.findById("order1")).thenReturn(Optional.of(existing));
        when(ezeeChargePostService.post(existing)).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setChargePostStatus("QUEUED");
            o.setChargePostRequestId("2805");
            return o;
        });
        when(orderRepository.save(existing)).thenReturn(existing);

        Order result = svc.updateOrderStatus("order1", "CHECKED", "staff1");

        assertEquals("QUEUED", result.getChargePostStatus());
        assertEquals("2805", result.getChargePostRequestId());
    }

    @Test
    void updateOrderStatus_toChecked_alreadyQueued_doesNotRetryChargePost() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        existing.setChargePostStatus("QUEUED");

        when(orderStatusService.getAllStatuses()).thenReturn(List.of(
                new com.hostel.ordering.model.OrderStatusConfig("CHECKED", "Checked", "checked", false)));
        when(orderRepository.findById("order1")).thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        svc.updateOrderStatus("order1", "CHECKED", "staff1");

        org.mockito.Mockito.verifyNoInteractions(ezeeChargePostService);
    }

    @Test
    void updateOrderStatus_toChecked_previouslyFailed_retriesChargePost() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        existing.setChargePostStatus("FAILED");

        when(orderStatusService.getAllStatuses()).thenReturn(List.of(
                new com.hostel.ordering.model.OrderStatusConfig("CHECKED", "Checked", "checked", false)));
        when(orderRepository.findById("order1")).thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        svc.updateOrderStatus("order1", "CHECKED", "staff1");

        org.mockito.Mockito.verify(ezeeChargePostService).post(existing);
    }

    @Test
    void updateOrderStatus_toCancelled_chargeQueued_voidsIt() {
        OrderService svc = new OrderService(orderRepository, fcmNotificationService, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("ORDERED");
        existing.setChargePostStatus("QUEUED");
        existing.setChargePostRequestId("2805");

        when(orderStatusService.getAllStatuses()).thenReturn(List.of(
                new com.hostel.ordering.model.OrderStatusConfig("CANCELLED", "Cancelled", "cancelled", false)));
        when(orderRepository.findById("order1")).thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        svc.updateOrderStatus("order1", "CANCELLED", "staff1");

        org.mockito.Mockito.verify(ezeeChargePostService).voidPost(existing);
    }

    @Test
    void updateOrderStatus_toCancelled_chargeNotQueued_doesNotCallVoid() {
        OrderService svc = new OrderService(orderRepository, fcmNotificationService, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("ORDERED");

        when(orderStatusService.getAllStatuses()).thenReturn(List.of(
                new com.hostel.ordering.model.OrderStatusConfig("CANCELLED", "Cancelled", "cancelled", false)));
        when(orderRepository.findById("order1")).thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        svc.updateOrderStatus("order1", "CANCELLED", "staff1");

        org.mockito.Mockito.verifyNoInteractions(ezeeChargePostService);
    }
```

Add the necessary imports (`OrderRepository`, `OrderStatusService`,
`AuditService`, `Optional`, `Mockito.verifyNoInteractions`,
`com.hostel.ordering.model.MenuItem`) to `OrderServiceTest.java` alongside
the existing ones.

- [ ] **Step 2: Run test, verify fails**
  Run: `mvn -q -Dtest=OrderServiceTest test`
  Expected: FAIL — compile error, `OrderService` constructor has 6 args, not 7

- [ ] **Step 3: Wire in the new dependency**

```java
package com.hostel.ordering.service;

import com.hostel.ordering.ezee.EzeeChargePostService;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import com.hostel.ordering.model.OrderStatusConfig;
import com.hostel.ordering.repository.MenuItemRepository;
import com.hostel.ordering.repository.OrderRepository;
import com.hostel.ordering.repository.OrderRepositoryCustom.SearchCriteria;
import com.hostel.ordering.repository.OtherEssentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final int MAX_ITEMS = 50;
    private static final int MAX_QUANTITY = 50;

    private final OrderRepository orderRepository;
    private final FCMNotificationService fcmNotificationService;
    private final AuditService auditService;
    private final OrderStatusService orderStatusService;
    private final MenuItemRepository menuItemRepository;
    private final OtherEssentialRepository otherEssentialRepository;
    private final EzeeChargePostService ezeeChargePostService;

    public OrderService(OrderRepository orderRepository,
                        FCMNotificationService fcmNotificationService,
                        AuditService auditService,
                        OrderStatusService orderStatusService,
                        MenuItemRepository menuItemRepository,
                        OtherEssentialRepository otherEssentialRepository,
                        EzeeChargePostService ezeeChargePostService) {
        this.orderRepository = orderRepository;
        this.fcmNotificationService = fcmNotificationService;
        this.auditService = auditService;
        this.orderStatusService = orderStatusService;
        this.menuItemRepository = menuItemRepository;
        this.otherEssentialRepository = otherEssentialRepository;
        this.ezeeChargePostService = ezeeChargePostService;
    }

    public Order createOrder(Order order) {
        repriceOrder(order);
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(System.currentTimeMillis());
        if (order.getCreatedBy() == null || order.getCreatedBy().isEmpty()) {
            order.setCreatedBy("Guest");
        }
        order.setUpdatedBy(order.getCreatedBy());
        if (order.getStatus() == null || order.getStatus().isEmpty()) {
            order.setStatus("ORDERED");
        }
        Order saved = orderRepository.save(order);
        log.info("New order created for {} in {}", saved.getBookingName(), saved.getDormitory());
        fcmNotificationService.sendNewOrderNotification(saved);
        auditService.logAction("ORDER_CREATED", "Created order for " + saved.getBookingName() + " in " + saved.getDormitory());
        ezeeChargePostService.postAsync(saved);
        return saved;
    }

    // ... repriceOrder, getOrder, getFilteredOrders unchanged ...

    public Order updateOrderStatus(String id, String status, String updatedBy) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be empty");
        }

        Set<String> validStatuses = orderStatusService.getAllStatuses().stream()
                .map(OrderStatusConfig::getValue)
                .collect(Collectors.toSet());

        if (!validStatuses.contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Valid statuses: " + validStatuses);
        }

        return orderRepository.findById(id)
                .map(order -> {
                    String oldStatus = order.getStatus();
                    order.setStatus(status);
                    order.setUpdatedAt(System.currentTimeMillis());
                    if (updatedBy != null) {
                        order.setUpdatedBy(updatedBy);
                    }

                    if ("CHECKED".equalsIgnoreCase(status)
                            && (order.getChargePostStatus() == null || "FAILED".equals(order.getChargePostStatus()))) {
                        ezeeChargePostService.post(order);
                    } else if ("CANCELLED".equalsIgnoreCase(status)
                            && "QUEUED".equals(order.getChargePostStatus())) {
                        ezeeChargePostService.voidPost(order);
                    }

                    Order updated = orderRepository.save(order);
                    log.info("Order for {} status updated: {} -> {}", order.getBookingName(), oldStatus, status);
                    auditService.logAction("ORDER_STATUS_UPDATED", "Status updated for " + order.getBookingName() + ": " + oldStatus + " -> " + status);

                    if ("CANCELLED".equalsIgnoreCase(status)) {
                        fcmNotificationService.sendOrderCancelledNotification(updated);
                    }

                    return updated;
                })
                .orElse(null);
    }

    // ... deleteOrder, deleteAllOrders, deleteFilteredOrders unchanged ...
}
```

Keep every other method in the file exactly as it was — only the constructor
signature/fields, `createOrder`'s last line before `return saved;`, and
`updateOrderStatus`'s body change.

- [ ] **Step 4: Run test, verify passes**
  Run: `mvn -q -Dtest=OrderServiceTest test`
  Expected: PASS

- [ ] **Step 5: Run the full test suite to catch any other callers of the
  old 6-arg constructor**
  Run: `mvn -q test`
  Expected: PASS — grep first with
  `grep -rn "new OrderService(" src/` to fix any other call sites before
  running if the grep finds more than the one in `OrderServiceTest.java`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hostel/ordering/service/OrderService.java src/test/java/com/hostel/ordering/service/OrderServiceTest.java
git commit -m "feat: auto-post chargepost to eZee on order creation, retry at CHECKED, void on cancel"
```

---

### Task 8: Config wiring

**Files:**
- Modify: `src/main/resources/application-prod.yml`

**Interfaces:** none — this task only supplies the `@Value` defaults already
referenced in Tasks 4 and 6 (`ezee.endpoint`, `ezee.auth-code`, `ezee.mock`,
`ezee.outlet`) with production env-var overrides.

- [ ] **Step 1: Add the block**

```yaml
ezee:
  endpoint: ${EZEE_ENDPOINT:https://live.ipms247.com/index.php/page/service.pos2pms}
  auth-code: ${EZEE_AUTH_CODE:}
  outlet: ${EZEE_OUTLET:Cafe}
  mock: ${EZEE_MOCK:false}
```

Add this as a new top-level key in `application-prod.yml`, alongside
`server`, `spring`, `logging`, `management`, `cors`.

- [ ] **Step 2: Verify the app still starts locally with mock mode on**
  Run: `EZEE_MOCK=true MONGODB_URI=<local-mongo-uri> mvn spring-boot:run -Dspring-boot.run.profiles=prod`
  Expected: starts without a `BeanCreationException` for `EzeeClient`/`EzeeChargePostService`

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application-prod.yml
git commit -m "chore: add ezee.* config for chargepost integration"
```

---

## Post-plan setup (not code — operational prerequisites)

These come from the original doc's "Not yet decided" list and are still
business/ops steps, not implementation tasks:

- Get the real `EZEE_AUTH_CODE` and confirm POS2PMS is enabled for the
  "Cafe" outlet on the eZee account (separate toggle from Optimus).
- For each of the 6 dormitories, set `ezeeRoomNumber` (private rooms) or
  `ezeeRoomType` (shared dorms, matching eZee's exact `roomtype` string) via
  `PUT /config/dormitories/{id}` — there are only 6, do this by hand once
  real room-type strings are confirmed against a live `roomlist` call.
- Confirm `"Restaurant Charge"` is the literal string eZee expects in the
  `charge` field for this account (the doc flags this may need to match
  whatever's selected in the eZee UI dropdown today) — if not, override via
  no current env var exists for it; if it turns out to vary, that's a small
  follow-up (`ezee.charge-label` `@Value`, same pattern as `ezee.outlet`).

## Execution Handoff

Plan complete, saved to
`docs/superpowers/plans/2026-08-22-ezee-chargepost-integration.md`. Two
execution options:

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task,
review each, fast iteration.

**2. Inline Execution** — execute tasks in this session via
`superpowers:executing-plans`, batch execution with checkpoints.

Which approach?
