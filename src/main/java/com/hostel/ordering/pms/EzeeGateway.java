package com.hostel.ordering.pms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The only door to eZee for hub features. Every call takes a rate-limiter slot first, then maps eZee's
 * HTTP-200-with-error-body replies to Pms exceptions. Bodies and credentials are never logged, and
 * exception messages are fixed strings because they reach the WARN log.
 */
@Component
public class EzeeGateway {

    private static final Logger log = LoggerFactory.getLogger(EzeeGateway.class);

    private static final Set<String> AUTH_CODES = Set.of("611", "301", "303", "602", "612", "614", "121");
    private static final Set<String> NOT_ENABLED_CODES = Set.of("201", "302");
    private static final Pattern NO_DATA_MESSAGE = Pattern.compile("no (data|records) found", Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_DATA_REPLY =
            Pattern.compile("(sorry\\s+)?no (data|records) found\\.?", Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_STATUS = Pattern.compile("<status>\\s*([^<]*?)\\s*</status>");
    private static final Pattern XML_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

    // Trailing-token check stops a CSV line like "101,..." parsing as the number 101.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final PmsProperties props;
    private final PmsRateLimiter limiter;
    private final PmsTransport transport;
    private final String baseUrl;
    private final String authCode;
    private final String hotelCode;

    public EzeeGateway(PmsProperties props, PmsRateLimiter limiter, PmsTransport transport,
            @Value("${ezee.base-url:https://live.ipms247.com}") String baseUrl,
            @Value("${ezee.auth-code:}") String authCode,
            @Value("${ezee.hotel-code:}") String hotelCode) {
        this.props = props;
        this.limiter = limiter;
        this.transport = transport;
        this.baseUrl = baseUrl;
        this.authCode = authCode;
        this.hotelCode = hotelCode;
    }

    /** A JSON reply; {@link NullNode} when eZee says there is no data. */
    public JsonNode json(EzeeFamily family, String requestType, Map<String, Object> params, PmsPriority priority) {
        String body = exchange(family, requestType, params, priority).strip();
        if (body.isEmpty() || NO_DATA_REPLY.matcher(body).matches()) {
            return NullNode.instance;
        }
        JsonNode node = parse(body);
        if (node == null || !node.isContainerNode()) {
            throw new PmsUpstreamException("PMS reply was not JSON");
        }
        return isEmpty(node) ? NullNode.instance : node;
    }

    /** A CSV, XML or plain reply returned untouched; empty string when eZee says there is no data. */
    public String text(EzeeFamily family, String requestType, Map<String, Object> params, PmsPriority priority) {
        String body = exchange(family, requestType, params, priority);
        String trimmed = body.strip();
        if (trimmed.isEmpty() || NO_DATA_REPLY.matcher(trimmed).matches()) {
            return "";
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            JsonNode node = parse(trimmed);
            if (node != null) {
                return isEmpty(node) ? "" : body;
            }
        }
        if (family == EzeeFamily.POS2PMS) {
            var status = XML_STATUS.matcher(trimmed);
            if (!status.find() || !"ok".equalsIgnoreCase(status.group(1))) {
                throw new PmsUpstreamException("PMS returned an error");
            }
        }
        return body;
    }

    private String exchange(EzeeFamily family, String requestType, Map<String, Object> params, PmsPriority priority) {
        // Kill switch: checked before the limiter so a disabled gateway spends no slot and does no I/O.
        if (!props.isEnabled()) {
            throw new PmsUpstreamException("PMS integration disabled");
        }
        limiter.acquire(priority);
        Request req = build(family, requestType, params);
        int maxBytes = family.maxBytesFor(requestType);
        long start = System.nanoTime();
        PmsTransport.Reply reply;
        try {
            reply = transport.send(req.method(), req.url(), req.headers(), req.body(), maxBytes);
        } catch (RuntimeException e) {
            log.warn("eZee {} {} failed after {} ms", family, requestType, millisSince(start));
            throw e instanceof PmsUpstreamException u ? u : new PmsUpstreamException("PMS request failed");
        }
        log.info("eZee {} {} -> HTTP {} in {} ms", family, requestType, reply.status(), millisSince(start));
        if (reply.status() == 429) {
            throw new PmsBusyException("PMS upstream rate limited");
        }
        if (reply.status() < 200 || reply.status() >= 300) {
            throw new PmsUpstreamException("PMS upstream HTTP error");
        }
        String body = reply.body() == null ? "" : reply.body();
        // The transport caps bytes while reading; this guards a transport that did not.
        if (body.length() > maxBytes) {
            throw new PmsUpstreamException("PMS reply too large");
        }
        return body;
    }

    /** True for a no-data reply; throws for auth, not-enabled and other error markers; false for success. */
    private static boolean isEmpty(JsonNode node) {
        if (!node.isObject()) {
            return false;
        }
        JsonNode error = null;
        String code = null;
        String message = "";
        if (node.path("Errors").isObject()) {
            error = node.get("Errors");
            code = error.path("ErrorCode").asText("");
            message = error.path("ErrorMessage").asText("");
        } else if (node.has("error_code")) {
            code = node.path("error_code").asText("");
            message = node.path("error_message").asText("");
        } else if (node.path("0").path("Error Details").isObject()) {
            error = node.path("0").path("Error Details");
            code = error.path("Error_Code").asText("");
            message = error.path("Error_Message").asText("");
        }
        if (code == null) {
            if ("error".equalsIgnoreCase(node.path("status").asText(""))) {
                throw new PmsUpstreamException("PMS returned an error");
            }
            return false;
        }
        if ("0".equals(code)) {
            return false;
        }
        if (AUTH_CODES.contains(code)) {
            throw new PmsAuthException("PMS rejected the credentials");
        }
        if (NOT_ENABLED_CODES.contains(code)) {
            throw new PmsNotEnabledException("PMS request type not enabled for this hotel");
        }
        if ("210".equals(code) || NO_DATA_MESSAGE.matcher(message).find()) {
            return true;
        }
        throw new PmsUpstreamException("PMS returned an error");
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            return null; // the parser message may quote the body, so it is dropped
        }
    }

    private record Request(String method, String url, Map<String, String> headers, String body) {}

    private Request build(EzeeFamily family, String requestType, Map<String, Object> params) {
        String url = baseUrl + family.pathFor(requestType);
        Map<String, String> jsonType = Map.of("Content-Type", "application/json");
        switch (family.auth) {
            case QUERY -> {
                StringBuilder q = new StringBuilder();
                appendQuery(q, "request_type", requestType);
                appendQuery(q, "HotelCode", hotelCode);
                appendQuery(q, "APIKey", authCode);
                params.forEach((k, v) -> appendQuery(q, k, String.valueOf(v)));
                return new Request("GET", url + "?" + q, Map.of(), null);
            }
            case RES_REQUEST -> {
                ObjectNode root = MAPPER.createObjectNode();
                ObjectNode req = root.putObject("RES_Request");
                req.put("Request_Type", requestType);
                ObjectNode auth = req.putObject("Authentication");
                params.forEach((k, v) -> {
                    // RetrieveListofBills wants BookingId inside Authentication, so a map there is merged in.
                    if ("Authentication".equals(k) && v instanceof Map) {
                        auth.setAll((ObjectNode) MAPPER.valueToTree(v));
                    } else {
                        req.set(k, MAPPER.valueToTree(v));
                    }
                });
                req.put("Request_Type", requestType);
                auth.put("HotelCode", hotelCode);
                auth.put("AuthCode", authCode);
                return new Request("POST", url, jsonType, root.toString());
            }
            case XML -> {
                StringBuilder x = new StringBuilder("<?xml version=\"1.0\" standalone=\"yes\"?><request><auth>")
                        .append(xmlEscape(authCode)).append("</auth><oprn>").append(xmlEscape(requestType)).append("</oprn>");
                params.forEach((k, v) -> {
                    if (!XML_NAME.matcher(k).matches()) {
                        throw new IllegalArgumentException("Invalid POS2PMS parameter name");
                    }
                    x.append('<').append(k).append('>').append(xmlEscape(String.valueOf(v))).append("</").append(k).append('>');
                });
                return new Request("POST", url, Map.of("Content-Type", "text/xml"), x.append("</request>").toString());
            }
            case HEADER -> {
                ObjectNode root = MAPPER.createObjectNode();
                root.put("request_type", requestType);
                ObjectNode body = root.putObject("body");
                body.put("hotel_id", hotelCode);
                body.setAll((ObjectNode) MAPPER.valueToTree(params));
                body.put("hotel_id", hotelCode);
                return new Request("POST", url, Map.of("Content-Type", "application/json", "AUTH_CODE", authCode),
                        root.toString());
            }
            case BODY_AUTHCODE -> {
                return new Request("POST", url, jsonType, credentialBody(params, "authcode", "hotel_code"));
            }
            case BODY_AUTHKEY -> {
                return new Request("POST", url, jsonType, credentialBody(params, "authkey", "hotel_code"));
            }
            default -> throw new IllegalStateException("Unhandled eZee auth style");
        }
    }

    /** {@code {keyField: auth code, hotelField: hotel code, ...params}}; the credentials win over a same-named param. */
    private String credentialBody(Map<String, Object> params, String keyField, String hotelField) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put(keyField, authCode);
        body.put(hotelField, hotelCode);
        body.setAll((ObjectNode) MAPPER.valueToTree(params));
        body.put(keyField, authCode);
        body.put(hotelField, hotelCode);
        return body.toString();
    }

    private static void appendQuery(StringBuilder q, String key, String value) {
        if (q.length() > 0) {
            q.append('&');
        }
        q.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
