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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * The real eZee transport on the JDK HTTP client. Failures carry fixed messages and no cause: a JDK
 * exception message can hold the URL, and the RESERVATION_GET URL holds the API key.
 */
@Component
public class JdkPmsTransport implements PmsTransport {

    private static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(15);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    /** Runs each exchange so the caller can abandon it at the deadline; idle threads die after 60 s. */
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ezee-http");
        t.setDaemon(true);
        return t;
    });
    private final Duration deadline;

    public JdkPmsTransport() {
        this(DEFAULT_DEADLINE);
    }

    /** @param deadline total time for the whole exchange, headers plus body */
    JdkPmsTransport(Duration deadline) {
        this.deadline = deadline;
    }

    @Override
    public Reply send(String method, String url, Map<String, String> headers, String body, int maxBytes) {
        HttpRequest.Builder req;
        try {
            req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(deadline)
                    .header("User-Agent", "openAPI-mosaic")
                    .method(method, body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(req::header);
        } catch (IllegalArgumentException e) {
            throw new PmsUpstreamException("PMS request failed");
        }
        HttpRequest request = req.build();
        return exchange(() -> {
            HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return new Opened(resp.statusCode(), resp.body());
        }, maxBytes);
    }

    /** A response whose headers have arrived and whose body is still to be read. */
    record Opened(int status, InputStream body) {}

    interface Opener {
        Opened open() throws IOException, InterruptedException;
    }

    /**
     * Opens and reads the reply on a worker under one overall deadline. On timeout the worker is
     * interrupted (which unblocks the JDK body stream) and the stream is closed.
     */
    Reply exchange(Opener opener, int maxBytes) {
        AtomicReference<InputStream> stream = new AtomicReference<>();
        Future<Reply> work = workers.submit(() -> {
            Opened opened = opener.open();
            stream.set(opened.body());
            try (InputStream in = opened.body()) {
                return new Reply(opened.status(), readCapped(in, maxBytes));
            }
        });
        try {
            return work.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new PmsUpstreamException("PMS request timed out");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PmsUpstreamException u) {
                throw u;
            }
            throw new PmsUpstreamException("PMS request failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PmsUpstreamException("PMS request interrupted");
        } finally {
            work.cancel(true);
            closeQuietly(stream.get());
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

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // the exchange already failed or finished; nothing to report
        }
    }
}
