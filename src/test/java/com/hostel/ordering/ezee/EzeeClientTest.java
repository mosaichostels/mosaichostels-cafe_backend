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
    void postRoomQuery_sendsXmlAndParsesFlatResponseAndRoomRows() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pos2pms", exchange -> {
            String responseXml = "<?xml version='1.0' standalone='yes'?>"
                    + "<response><status>ok</status><masterfolio>8</masterfolio>"
                    + "<roomrows><row><room>106</room><masterfolio>10</masterfolio></row>"
                    + "<row><room>106</room><masterfolio>11</masterfolio></row></roomrows></response>";
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
        fields.put("oprn", "roomquery");
        fields.put("room", "106");

        RoomQueryResult result = client.postRoomQuery(fields);

        assertEquals("ok", result.fields().get("status"));
        assertEquals("8", result.fields().get("masterfolio"));
        assertEquals(2, result.rows().size());
        assertEquals("10", result.rows().get(0).get("masterfolio"));
        assertEquals("11", result.rows().get(1).get("masterfolio"));
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
