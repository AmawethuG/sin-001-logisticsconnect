package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HubServiceApp {

    private static final Logger log = LoggerFactory.getLogger(HubServiceApp.class);

    public static void main(String[] args) {
        String ingestionUrl = System.getenv().getOrDefault("INGESTION_URL", "http://localhost:7050");
        HubCatalog catalog = new HubCatalog(ingestionUrl);

        Javalin app = Javalin.create().start(7051);

        // ingestion-service being down is an upstream problem, not a bug in this service.
        app.exception(IOException.class, (e, ctx) ->
                ctx.status(503).json(Map.of("error", "Cannot load hubs from ingestion-service: " + e.getMessage())));

        app.get("/health", ctx -> ctx.result("OK"));

        // Optional ?province=gauteng filter.
        app.get("/hubs", ctx -> {
            List<Hub> hubs = catalog.all();
            String province = ctx.queryParam("province");
            if (province != null) {
                hubs = hubs.stream().filter(h -> HubCatalog.sameName(h.province(), province)).toList();
            }
            ctx.json(hubs);
        });

        app.get("/hubs/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            Optional<Hub> hub = catalog.find(hubId);
            if (hub.isPresent()) {
                ctx.json(hub.get());
            } else {
                ctx.status(404).json(Map.of("error", "Unknown hub: " + hubId));
            }
        });

        app.get("/provinces", ctx -> ctx.json(catalog.provinces()));

        // Re-pull the cleaned data from ingestion-service on demand.
        app.post("/hubs/refresh", ctx -> ctx.json(Map.of("hubs", catalog.refresh().size())));

        try {
            log.info("Loaded {} hubs from {}", catalog.refresh().size(), ingestionUrl);
        } catch (IOException e) {
            log.warn("ingestion-service not reachable yet ({}); will load hubs on first request", e.getMessage());
        }
    }
}
