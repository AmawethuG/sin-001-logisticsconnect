package co.wethinkcode.logisticsconnect;

/** Result of POST /delay-stage/{hubId}. */
public record StageChange(String hubId, int stage, int previousStage, boolean changed) {
}
