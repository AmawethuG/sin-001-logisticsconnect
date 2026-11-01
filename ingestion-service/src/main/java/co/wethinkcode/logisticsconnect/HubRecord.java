package co.wethinkcode.logisticsconnect;

import java.util.List;

/**
 * A cleaned, de-duplicated hub / sorting center.
 *
 * @param hubId         canonical hub id (the lowest id among the duplicates that were merged)
 * @param province      canonical province name, e.g. "KwaZulu-Natal"; null if it could not be determined
 * @param sortingCenter title-cased sorting center name
 * @param active        true/false, or null when the source data never said ("unknown", "N/A", ...)
 * @param aliases       other hub ids that were merged into this record, so old ids can still be resolved
 */
public record HubRecord(String hubId, String province, String sortingCenter, Boolean active, List<String> aliases) {
}
