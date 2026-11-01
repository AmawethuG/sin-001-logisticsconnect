package co.wethinkcode.logisticsconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the messy rows of hubs-global.csv into clean, de-duplicated {@link HubRecord}s.
 *
 * <p>Pipeline: clean every field -> fill missing provinces -> merge duplicates.
 *
 * <p>Duplicate strategy: two rows describe the same hub when they have the same sorting center
 * (case/padding-insensitive) in the same province. Among duplicates the lowest hub id survives (the
 * first one registered) and the others are kept as {@code aliases}. Conflicting {@code active} flags
 * are resolved by majority vote; on a tie the highest id (the most recently registered row) wins.
 */
public final class HubCleaner {

    private static final Logger log = LoggerFactory.getLogger(HubCleaner.class);

    private static final Set<String> PLACEHOLDERS =
            Set.of("", "n/a", "na", "tbd", "unknown", "-", "nan", "null", "none");
    private static final Set<String> TRUE_VALUES = Set.of("y", "yes", "1", "true", "t");
    private static final Set<String> FALSE_VALUES = Set.of("n", "no", "0", "false", "f");

    private static final Pattern ID_PATTERN = Pattern.compile("^h[-_ ]?(\\d+)$", Pattern.CASE_INSENSITIVE);

    /** Shorter ids first, then alphabetical, so H-99 sorts before H-100. */
    private static final Comparator<String> ID_ORDER =
            Comparator.<String>comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    /** Canonical province names, keyed by their letters-only lower-case form. */
    private static final Map<String, String> PROVINCES = new HashMap<>();

    static {
        for (String province : List.of("Eastern Cape", "Free State", "Gauteng", "KwaZulu-Natal", "Limpopo",
                "Mpumalanga", "North West", "Northern Cape", "Western Cape")) {
            PROVINCES.put(lettersOnly(province), province);
        }
        PROVINCES.put("kzn", "KwaZulu-Natal");
    }

    private HubCleaner() {
    }

    /** A row after field-level cleaning, before duplicates are merged. */
    private record Cleaned(String id, String province, String name, Boolean active) {
    }

    public static List<HubRecord> clean(List<RawHub> rows) {
        List<Cleaned> cleaned = new ArrayList<>();
        for (RawHub row : rows) {
            String id = cleanId(row.hubId());
            if (id == null) {
                log.warn("Dropping row with no usable hub id: {}", row);
                continue;
            }
            cleaned.add(new Cleaned(id, cleanProvince(row.province()), cleanName(row.sortingCenter()),
                    parseFlag(row.active())));
        }
        return deduplicate(fillMissingProvinces(cleaned));
    }

    // ---- field-level cleaning -------------------------------------------------------------

    /** Trims, collapses whitespace (including non-breaking spaces) and turns placeholders into null. */
    static String text(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip();
        return PLACEHOLDERS.contains(value.toLowerCase(Locale.ROOT)) ? null : value;
    }

    static String cleanId(String raw) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        Matcher matcher = ID_PATTERN.matcher(value);
        return matcher.matches() ? "H-" + matcher.group(1) : value.toUpperCase(Locale.ROOT);
    }

    static String cleanName(String raw) {
        String value = text(raw);
        return value == null ? null : titleCase(value);
    }

    static String cleanProvince(String raw) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        String canonical = PROVINCES.get(lettersOnly(value));
        if (canonical == null) {
            log.warn("Unrecognised province '{}', keeping it as-is", value);
            return titleCase(value);
        }
        return canonical;
    }

    static Boolean parseFlag(String raw) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(lower)) {
            return Boolean.TRUE;
        }
        if (FALSE_VALUES.contains(lower)) {
            return Boolean.FALSE;
        }
        log.warn("Unrecognised active flag '{}', treating as unknown", value);
        return null;
    }

    static String titleCase(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean startOfWord = true;
        for (char c : value.toCharArray()) {
            out.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
            startOfWord = c == ' ' || c == '-';
        }
        return out.toString();
    }

    private static String lettersOnly(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    // ---- cross-row cleaning ---------------------------------------------------------------

    /**
     * A row with no province but a sorting center that appears elsewhere under exactly one province
     * (e.g. H-508 "Pretoria North" vs H-502 "Pretoria North, Gauteng") inherits that province.
     */
    private static List<Cleaned> fillMissingProvinces(List<Cleaned> rows) {
        Map<String, Set<String>> provincesByName = new HashMap<>();
        for (Cleaned row : rows) {
            if (row.name() != null && row.province() != null) {
                provincesByName.computeIfAbsent(nameKey(row.name()), k -> new HashSet<>()).add(row.province());
            }
        }

        List<Cleaned> filled = new ArrayList<>();
        for (Cleaned row : rows) {
            Set<String> known = row.name() == null ? null : provincesByName.get(nameKey(row.name()));
            if (row.province() == null && known != null && known.size() == 1) {
                String province = known.iterator().next();
                log.info("{}: missing province filled in as {} (from other '{}' rows)", row.id(), province, row.name());
                filled.add(new Cleaned(row.id(), province, row.name(), row.active()));
            } else {
                filled.add(row);
            }
        }
        return filled;
    }

    private static List<HubRecord> deduplicate(List<Cleaned> rows) {
        Map<String, List<Cleaned>> groups = new LinkedHashMap<>();
        for (Cleaned row : rows) {
            // Rows we cannot identify by name + province are never merged with anything.
            String key = (row.name() == null || row.province() == null)
                    ? "id:" + row.id()
                    : nameKey(row.name()) + "|" + row.province();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<HubRecord> hubs = new ArrayList<>();
        for (List<Cleaned> group : groups.values()) {
            hubs.add(merge(group));
        }
        hubs.sort(Comparator.comparing(HubRecord::hubId, ID_ORDER));
        return hubs;
    }

    private static HubRecord merge(List<Cleaned> group) {
        List<Cleaned> byId = new ArrayList<>(group);
        byId.sort(Comparator.comparing(Cleaned::id, ID_ORDER));

        Cleaned survivor = byId.get(0);
        List<String> aliases = byId.stream()
                .map(Cleaned::id)
                .filter(id -> !id.equals(survivor.id()))
                .distinct()
                .toList();
        Boolean active = resolveActive(byId);

        if (!aliases.isEmpty()) {
            log.info("{} '{}' ({}): merged duplicates {} -> active={}",
                    survivor.id(), survivor.name(), survivor.province(), aliases, active);
        }
        return new HubRecord(survivor.id(), survivor.province(), survivor.name(), active, aliases);
    }

    /** Majority vote among known flags; on a tie the most recent (highest id) known value wins. */
    private static Boolean resolveActive(List<Cleaned> byIdAscending) {
        long yes = byIdAscending.stream().filter(c -> Boolean.TRUE.equals(c.active())).count();
        long no = byIdAscending.stream().filter(c -> Boolean.FALSE.equals(c.active())).count();
        if (yes > no) {
            return Boolean.TRUE;
        }
        if (no > yes) {
            return Boolean.FALSE;
        }
        for (int i = byIdAscending.size() - 1; i >= 0; i--) {
            if (byIdAscending.get(i).active() != null) {
                return byIdAscending.get(i).active();
            }
        }
        return null;
    }

    private static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
