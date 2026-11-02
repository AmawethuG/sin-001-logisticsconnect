package co.wethinkcode.logisticsconnect;

/** Body of POST /delay-stage/{hubId}, e.g. {"stage": 3}. Boxed so a missing field can be rejected. */
public record StageRequest(Integer stage) {
}
