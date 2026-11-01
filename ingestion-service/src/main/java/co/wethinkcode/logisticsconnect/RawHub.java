package co.wethinkcode.logisticsconnect;

/** One row of hubs-global.csv exactly as it appears in the file (untrimmed, uncleaned). */
public record RawHub(String hubId, String province, String sortingCenter, String active) {
}
