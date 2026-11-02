package co.wethinkcode.logisticsconnect;

/** Current delay stage of a hub, as returned by GET /delay-stage/{hubId}. */
public record DelayStage(String hubId, int stage) {
}
