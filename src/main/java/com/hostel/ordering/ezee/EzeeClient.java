package com.hostel.ordering.ezee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

// Low-level transport for eZee's POS2PMS XML API and the Kiosk Connectivity
// JSON API (AddExtraCharge). One job: turn field maps into HTTP requests and
// responses back into field maps. Callers (the admin eZee-search endpoint,
// EzeeChargePostService) live elsewhere.
@Component
public class EzeeClient {

    private static final Logger log = LoggerFactory.getLogger(EzeeClient.class);

    private final String endpoint;
    private final String kioskEndpoint;
    private final String authCode;
    private final String hotelCode;
    private final boolean mock;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EzeeClient(
            @Value("${ezee.endpoint:https://live.ipms247.com/index.php/page/service.pos2pms}") String endpoint,
            @Value("${ezee.kiosk-endpoint:https://live.ipms247.com/index.php/page/service.kioskconnectivity}") String kioskEndpoint,
            @Value("${ezee.auth-code:}") String authCode,
            @Value("${ezee.hotel-code:}") String hotelCode,
            @Value("${ezee.mock:false}") boolean mock) {
        this.endpoint = endpoint;
        this.kioskEndpoint = kioskEndpoint;
        this.authCode = authCode;
        this.hotelCode = hotelCode;
        this.mock = mock;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        if (mock) {
            log.warn("EzeeClient running in MOCK mode — chargepost/roomquery calls will NOT reach live eZee PMS");
        }
    }

    public String getAuthCode() {
        return authCode;
    }

    // ponytail: temp lookup to find an ExtraChargeId in eZee admin — delete once used.
    public String listExtraCharges() {
        String url = "https://live.ipms247.com/booking/reservation_api/listing.php"
                + "?request_type=ExtraCharges&HotelCode=" + hotelCode + "&APIKey=" + authCode + "&language=en";
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("eZee ExtraCharges listing request failed", e);
        }
    }

    // Posts a charge to a reservation via Kiosk Connectivity's AddExtraCharge —
    // unlike POS2PMS chargepost, this doesn't need an outlet configured on the
    // PMS side, but eZee also exposes no API to void/remove it afterward.
    public Map<String, String> postExtraCharge(String bookingId, String folioNo, String chargeId,
                                                String amount, String qty, String comment) {
        if (mock) {
            return EzeeMockResponses.forOprn("addextracharge");
        }
        Map<String, Object> reservation = new LinkedHashMap<>();
        reservation.put("BookingId", bookingId);
        reservation.put("FolioNo", folioNo == null ? "" : folioNo);
        reservation.put("ChargeId", chargeId);
        reservation.put("Amount", amount);
        reservation.put("Qty", qty);
        reservation.put("Comment", comment);

        Map<String, Object> authentication = new LinkedHashMap<>();
        authentication.put("HotelCode", hotelCode);
        authentication.put("AuthCode", authCode);

        Map<String, Object> resRequest = new LinkedHashMap<>();
        resRequest.put("Request_Type", "AddExtraCharge");
        resRequest.put("Authentication", authentication);
        resRequest.put("Reservation", List.of(reservation));

        Map<String, Object> body = Map.of("RES_Request", resRequest);

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(kioskEndpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("eZee AddExtraCharge raw response: {}", response.body());
            return parseExtraChargeResponse(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("eZee AddExtraCharge request failed", e);
        }
    }

    private Map<String, String> parseExtraChargeResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            // eZee is inconsistent: success payloads use "Errors" (plural, ErrorCode 0),
            // but rejections use "Error" (singular) — check both or a rejection silently
            // parses as success.
            JsonNode errors = root.path("Errors");
            if (!errors.isArray()) errors = root.path("Error");
            String errorCode = errors.isArray() && !errors.isEmpty() ? errors.get(0).path("ErrorCode").asText() : "0";
            String errorMessage = errors.isArray() && !errors.isEmpty() ? errors.get(0).path("ErrorMessage").asText() : "";
            Map<String, String> result = new LinkedHashMap<>();
            if ("0".equals(errorCode)) {
                result.put("status", "ok");
                result.put("msg", root.path("Success").path("SuccessMsg").asText(errorMessage));
            } else {
                result.put("status", "error");
                result.put("msg", errorMessage.isEmpty() ? "eZee returned an error" : errorMessage);
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse eZee AddExtraCharge response JSON", e);
        }
    }

    private String send(LinkedHashMap<String, String> fields) {
        String requestXml = EzeeXmlUtil.buildRequest(fields);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofString(requestXml))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("eZee request failed for oprn=" + fields.get("oprn"), e);
        }
    }

    public Map<String, String> post(LinkedHashMap<String, String> fields) {
        if (mock) {
            return EzeeMockResponses.forOprn(fields.get("oprn"));
        }
        return EzeeXmlUtil.parseFlatResponse(send(fields));
    }

    public List<Map<String, String>> postForRoomRows(LinkedHashMap<String, String> fields) {
        if (mock) {
            return EzeeMockResponses.roomRowsForOprn(fields.get("oprn"));
        }
        String body = send(fields);
        log.info("eZee raw response for oprn={}: {}", fields.get("oprn"),
                body.length() > 1000 ? body.substring(0, 1000) + "...(truncated)" : body);
        return EzeeXmlUtil.parseRoomRows(body);
    }

    public RoomQueryResult postRoomQuery(LinkedHashMap<String, String> fields) {
        if (mock) {
            return new RoomQueryResult(
                    EzeeMockResponses.forOprn(fields.get("oprn")),
                    EzeeMockResponses.roomRowsForOprn(fields.get("oprn")));
        }
        String body = send(fields);
        log.info("eZee raw response for oprn={} room={}: {}", fields.get("oprn"), fields.get("room"),
                body.length() > 1000 ? body.substring(0, 1000) + "...(truncated)" : body);
        return new RoomQueryResult(EzeeXmlUtil.parseFlatResponse(body), EzeeXmlUtil.parseRoomRows(body));
    }
}
