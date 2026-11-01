package co.wethinkcode.logisticsconnect;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads hubs-global.csv into {@link RawHub} rows without cleaning anything. */
public final class HubCsvReader {

    private static final List<String> REQUIRED_COLUMNS = List.of("hub_id", "province", "sorting_center", "active");

    private HubCsvReader() {
    }

    public static List<RawHub> readResource(String resource) throws IOException, CsvException {
        try (InputStream in = HubCsvReader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new FileNotFoundException("Classpath resource not found: " + resource);
            }
            return read(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    public static List<RawHub> read(Reader reader) throws IOException, CsvException {
        try (CSVReader csv = new CSVReaderBuilder(reader).build()) {
            List<String[]> rows = csv.readAll();
            if (rows.isEmpty()) {
                return List.of();
            }

            // The header itself is messy (" Province "), so match columns on trimmed, lower-cased names.
            String[] header = rows.get(0);
            Map<String, Integer> columns = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                columns.put(header[i].replace("\uFEFF", "").strip().toLowerCase(Locale.ROOT), i);
            }
            for (String required : REQUIRED_COLUMNS) {
                if (!columns.containsKey(required)) {
                    throw new IllegalStateException("hubs-global.csv is missing required column: " + required);
                }
            }

            List<RawHub> hubs = new ArrayList<>();
            for (String[] row : rows.subList(1, rows.size())) {
                if (isBlank(row)) {
                    continue;
                }
                hubs.add(new RawHub(
                        cell(row, columns.get("hub_id")),
                        cell(row, columns.get("province")),
                        cell(row, columns.get("sorting_center")),
                        cell(row, columns.get("active"))));
            }
            return hubs;
        }
    }

    private static String cell(String[] row, int index) {
        return index < row.length ? row[index] : null;
    }

    private static boolean isBlank(String[] row) {
        for (String value : row) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
