package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class IngestionServiceApp {

    private static final Logger log = LoggerFactory.getLogger(IngestionServiceApp.class);

    public static void main(String[] args) throws Exception {
        // The CSV is a static resource, so clean it once at startup and serve the result from memory.
        List<RawHub> raw = HubCsvReader.readResource("/hubs-global.csv");
        List<HubRecord> hubs = HubCleaner.clean(raw);
        log.info("Cleaned {} raw rows into {} hubs", raw.size(), hubs.size());

        Javalin app = Javalin.create().start(7050);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/hubs", ctx -> ctx.json(hubs));

        // Accepts a canonical id or any alias of a merged duplicate (case/padding-insensitive).
        app.get("/hubs/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            Optional<HubRecord> hub = find(hubs, hubId);
            if (hub.isPresent()) {
                ctx.json(hub.get());
            } else {
                ctx.status(404).json(Map.of("error", "Unknown hub: " + hubId));
            }
        });
    }

    static Optional<HubRecord> find(List<HubRecord> hubs, String rawId) {
        String id = HubCleaner.cleanId(rawId);
        if (id == null) {
            return Optional.empty();
        }
        return hubs.stream()
                .filter(h -> h.hubId().equals(id) || h.aliases().contains(id))
                .findFirst();
    }
}
