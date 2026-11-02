package co.wethinkcode.logisticsconnect;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Holds the current delay stage per hub, in memory. A hub nobody has reported on is at stage 0
 * (no delay), so lookups never fail.
 */
public class DelayStageRegistry {

    public static final int MIN_STAGE = 0;
    public static final int MAX_STAGE = 8;

    private final ConcurrentMap<String, Integer> stages = new ConcurrentHashMap<>();

    public static boolean isValid(Integer stage) {
        return stage != null && stage >= MIN_STAGE && stage <= MAX_STAGE;
    }

    public int get(String hubId) {
        return stages.getOrDefault(hubId, MIN_STAGE);
    }

    /** Stores the new stage and returns the previous one. */
    public int set(String hubId, int stage) {
        Integer previous = stages.put(hubId, stage);
        return previous == null ? MIN_STAGE : previous;
    }

    public Map<String, Integer> all() {
        return new TreeMap<>(stages);
    }
}
