package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** A hub as served by ingestion-service's GET /hubs (already cleaned and de-duplicated). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Hub(String hubId, String province, String sortingCenter, Boolean active, List<String> aliases) {
}
