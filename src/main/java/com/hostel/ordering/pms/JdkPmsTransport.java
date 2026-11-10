package com.hostel.ordering.pms;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The real eZee transport on the JDK HTTP client. Failures carry fixed messages and no cause: a JDK
 * exception message can hold the URL, and the RESERVATION_GET URL holds the API key.
 */
@Component
public class JdkPmsTransport implements PmsTransport {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public Reply send(String method, String url, Map<String, String> headers, String body, int maxBytes) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header("User-Agent", "openAPI-mosaic")
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(req::header);
        try {
            HttpResponse<InputStream> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = resp.body()) {
                return new Reply(resp.statusCode(), readCapped(in, maxBytes));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PmsUpstreamException("PMS request interrupted");
        } catch (IOException | IllegalArgumentException e) {
            throw new PmsUpstreamException("PMS request failed");
        }
    }

    /** Reads at most {@code maxBytes + 1} bytes so an oversized reply is never buffered whole. */
    static String readCapped(InputStream in, int maxBytes) throws IOException {
        byte[] bytes = in.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new PmsUpstreamException("PMS reply too large");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
