package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * In-memory copy of the cleaned hubs published by ingestion-service. Loaded lazily, so hub-service
 * can start before ingestion-service is up, and cached so a request never re-parses anything.
 */
public class HubCatalog {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String ingestionUrl;

    private volatile List<Hub> hubs = List.of();

    public HubCatalog(String ingestionUrl) {
        this.ingestionUrl = ingestionUrl;
    }

    /** Re-fetches GET /hubs from ingestion-service and replaces the cache. */
    public synchronized List<Hub> refresh() throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ingestionUrl + "/hubs"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("ingestion-service returned HTTP " + response.statusCode());
            }
            hubs = List.copyOf(mapper.readValue(response.body(), new TypeReference<List<Hub>>() {
            }));
            return hubs;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling ingestion-service", e);
        }
    }

    public List<Hub> all() throws IOException {
        List<Hub> current = hubs;
        return current.isEmpty() ? refresh() : current;
    }

    /** Finds a hub by its canonical id or by the id of a duplicate that was merged into it. */
    public Optional<Hub> find(String rawId) throws IOException {
        String id = rawId.strip().toUpperCase(Locale.ROOT);
        return all().stream()
                .filter(h -> h.hubId().equals(id) || (h.aliases() != null && h.aliases().contains(id)))
                .findFirst();
    }

    public List<String> provinces() throws IOException {
        return all().stream()
                .map(Hub::province)
                .filter(p -> p != null)
                .distinct()
                .sorted()
                .toList();
    }

    /** Province names match regardless of case, spaces and hyphens ("kwazulu natal" == "KwaZulu-Natal"). */
    public static boolean sameName(String a, String b) {
        return a != null && b != null && key(a).equals(key(b));
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
