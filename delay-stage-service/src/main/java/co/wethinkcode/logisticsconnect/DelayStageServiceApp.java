package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.util.Locale;
import java.util.Map;

public class DelayStageServiceApp {

    public static void main(String[] args) {
        DelayStageRegistry registry = new DelayStageRegistry();

        Javalin app = Javalin.create().start(7052);

        app.get("/health", ctx -> ctx.result("OK"));

        // Every hub that has had a stage set, e.g. {"H-501": 3}.
        app.get("/delay-stage", ctx -> ctx.json(registry.all()));

        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = normalise(ctx.pathParam("hubId"));
            ctx.json(new DelayStage(hubId, registry.get(hubId)));
        });

        // Body: {"stage": 3}
        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = normalise(ctx.pathParam("hubId"));

            StageRequest body;
            try {
                body = ctx.bodyAsClass(StageRequest.class);
            } catch (Exception e) {
                body = null;
            }
            if (body == null || !DelayStageRegistry.isValid(body.stage())) {
                ctx.status(400).json(Map.of("error", "Body must be JSON like {\"stage\": 3}, with a stage from "
                        + DelayStageRegistry.MIN_STAGE + " to " + DelayStageRegistry.MAX_STAGE));
                return;
            }

            int stage = body.stage();
            int previous = registry.set(hubId, stage);
            boolean changed = stage != previous;

            ctx.json(new StageChange(hubId, stage, previous, changed));
        });
    }

    private static String normalise(String hubId) {
        return hubId.strip().toUpperCase(Locale.ROOT);
    }
}
