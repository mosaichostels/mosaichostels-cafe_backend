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
