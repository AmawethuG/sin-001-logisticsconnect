package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubCleanerTest {

    @Test
    void trimsPaddingAndCollapsesDoubleSpaces() {
        assertEquals("Cape Town Port", HubCleaner.cleanName("  cape  TOWN port "));
    }

    @Test
    void normalisesIdCasingAndPadding() {
        assertEquals("H-501", HubCleaner.cleanId("h-501"));
        assertEquals("H-502", HubCleaner.cleanId("H-502 "));
    }

    @Test
    void placeholdersBecomeNull() {
        for (String placeholder : List.of("", "  ", "N/A", "n/a", "TBD", "unknown", "-", "NaN")) {
            assertNull(HubCleaner.text(placeholder), "placeholder: '" + placeholder + "'");
        }
    }

    @Test
    void provinceSpellingVariantsMapToOneCanonicalName() {
        for (String variant : List.of("Kwa-Zulu Natal", "KwaZulu Natal", "kwazulu-natal", " KWAZULU-NATAL ")) {
            assertEquals("KwaZulu-Natal", HubCleaner.cleanProvince(variant), variant);
        }
        assertEquals("Western Cape", HubCleaner.cleanProvince("western cape"));
        assertNull(HubCleaner.cleanProvince(""));
    }

    @Test
    void flagsAreNormalisedToBooleans() {
        for (String yes : List.of("Y", "yes", "YES", "1", "true", "TRUE")) {
            assertEquals(Boolean.TRUE, HubCleaner.parseFlag(yes), yes);
        }
        for (String no : List.of("N", "no", "0", "FALSE", "false")) {
            assertEquals(Boolean.FALSE, HubCleaner.parseFlag(no), no);
        }
        assertNull(HubCleaner.parseFlag("unknown"));
        assertNull(HubCleaner.parseFlag("N/A"));
    }

    @Test
    void duplicatesAreMergedUnderTheLowestIdWithMajorityActiveFlag() {
        List<HubRecord> hubs = HubCleaner.clean(List.of(
                new RawHub("H-504", "Gauteng", "Johannesburg Central", "true"),
                new RawHub("H-500", " Gauteng ", "Johannesburg Central", "Y"),
                new RawHub("H-510", "Gauteng", "johannesburg central", "FALSE"),
                new RawHub("H-515", "Gauteng", "Johannesburg Central", "YES")));

        assertEquals(1, hubs.size());
        HubRecord hub = hubs.get(0);
        assertEquals("H-500", hub.hubId());
        assertEquals(List.of("H-504", "H-510", "H-515"), hub.aliases());
        assertEquals(Boolean.TRUE, hub.active());
    }

    @Test
    void activeTieIsBrokenByTheMostRecentRecord() {
        List<HubRecord> hubs = HubCleaner.clean(List.of(
                new RawHub("H-502", "Gauteng", "Pretoria North", "0"),
                new RawHub("H-508", "Gauteng", "Pretoria North", "yes")));

        assertEquals(Boolean.TRUE, hubs.get(0).active());
    }

    @Test
    void sameNameInDifferentProvincesIsNotMerged() {
        List<HubRecord> hubs = HubCleaner.clean(List.of(
                new RawHub("H-1", "Gauteng", "Central Hub", "Y"),
                new RawHub("H-2", "Free State", "Central Hub", "Y")));

        assertEquals(2, hubs.size());
    }

    @Test
    void missingProvinceIsFilledFromMatchingRow() {
        List<HubRecord> hubs = HubCleaner.clean(List.of(
                new RawHub("H-502", "gauteng", "Pretoria North", "0"),
                new RawHub("H-508", "", "Pretoria North", "yes")));

        assertEquals(1, hubs.size());
        assertEquals("Gauteng", hubs.get(0).province());
        assertEquals(List.of("H-508"), hubs.get(0).aliases());
    }

    @Test
    void realCsvCleansDownToTenHubs() throws Exception {
        List<RawHub> raw = HubCsvReader.readResource("/hubs-global.csv");
        List<HubRecord> hubs = HubCleaner.clean(raw);

        assertEquals(18, raw.size());
        assertEquals(10, hubs.size());

        HubRecord joburg = hubs.get(0);
        assertEquals("H-500", joburg.hubId());
        assertEquals("Johannesburg Central", joburg.sortingCenter());
        assertEquals(List.of("H-504", "H-510", "H-515"), joburg.aliases());

        HubRecord durban = hubs.stream().filter(h -> h.hubId().equals("H-503")).findFirst().orElseThrow();
        assertEquals("KwaZulu-Natal", durban.province());
        assertEquals(List.of("H-506", "H-516"), durban.aliases());

        HubRecord polokwane = hubs.stream().filter(h -> h.hubId().equals("H-511")).findFirst().orElseThrow();
        assertNull(polokwane.active());
    }

    @Test
    void lookupResolvesAliasesCaseInsensitively() throws Exception {
        List<HubRecord> hubs = HubCleaner.clean(HubCsvReader.readResource("/hubs-global.csv"));

        Optional<HubRecord> viaAlias = IngestionServiceApp.find(hubs, " h-515 ");
        assertTrue(viaAlias.isPresent());
        assertEquals("H-500", viaAlias.get().hubId());
        assertFalse(IngestionServiceApp.find(hubs, "H-999").isPresent());
    }
}
