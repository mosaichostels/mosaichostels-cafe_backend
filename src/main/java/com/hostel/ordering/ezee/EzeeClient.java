package com.hostel.ordering.ezee;

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

// Low-level transport for eZee's POS2PMS XML API. One job: turn a field map
// into an HTTP POST and the response body into a field map. Callers (the
// admin eZee-search endpoint, EzeeChargePostService) live elsewhere.
@Component
public class EzeeClient {

    private static final Logger log = LoggerFactory.getLogger(EzeeClient.class);

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
        if (mock) {
            log.warn("EzeeClient running in MOCK mode — chargepost/roomquery calls will NOT reach live eZee PMS");
        }
    }

    public String getAuthCode() {
        return authCode;
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
        return new RoomQueryResult(EzeeXmlUtil.parseFlatResponse(body), EzeeXmlUtil.parseRoomRows(body));
    }
}
