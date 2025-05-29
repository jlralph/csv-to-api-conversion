package com.example.csvtoapiconversion;

import org.junit.jupiter.api.*;

import com.example.csvtoapiconversion.CsvUtils;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CsvUtilsTest {

    private Path tempCsv;

    @BeforeEach
    void setup() throws IOException {
        // Create a temporary CSV file for testing
        tempCsv = Files.createTempFile("test-csvutils", ".csv");
        List<String> lines = Arrays.asList(
            "Asset,Contact,Owner,1.1.1.1,05/01/2025 08:00:00 AM,",
            "Asset2,Contact2,Owner2,2.2.2.2,05/01/2025 09:00:00 AM,05/01/2025 10:00:00 AM"
        );
        Files.write(tempCsv, lines);
    }

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(tempCsv);
    }

    @Test
    void testProcessCsv_activeAndDeactivated() throws IOException {
        Map<String, Set<String>> ownerToActiveIps = new HashMap<>();
        Map<String, Set<String>> contactToActiveIps = new HashMap<>();
        Map<String, Set<String>> ownerToDeactivatedIps = new HashMap<>();
        Map<String, Set<String>> contactToDeactivatedIps = new HashMap<>();

        CsvUtils.processCsv(
            tempCsv,
            null,
            ownerToActiveIps, contactToActiveIps,
            ownerToDeactivatedIps, contactToDeactivatedIps
        );

        assertTrue(ownerToActiveIps.get("Owner").contains("1.1.1.1"));
        assertTrue(contactToActiveIps.get("Contact").contains("1.1.1.1"));
        assertTrue(ownerToDeactivatedIps.get("Owner2").contains("2.2.2.2"));
        assertTrue(contactToDeactivatedIps.get("Contact2").contains("2.2.2.2"));
    }

    @Test
    void testProcessCsv_withStartTimestamp() throws IOException {
        Map<String, Set<String>> ownerToActiveIps = new HashMap<>();
        Map<String, Set<String>> contactToActiveIps = new HashMap<>();
        Map<String, Set<String>> ownerToDeactivatedIps = new HashMap<>();
        Map<String, Set<String>> contactToDeactivatedIps = new HashMap<>();

        // Set startTimestamp after the first row's create time
        LocalDateTime start = CsvUtils.parseDate("05/01/2025 08:30:00 AM");

        CsvUtils.processCsv(
            tempCsv,
            start,
            ownerToActiveIps, contactToActiveIps,
            ownerToDeactivatedIps, contactToDeactivatedIps
        );

        // First row should be filtered out
        assertNull(ownerToActiveIps.get("Owner"));
        assertTrue(ownerToDeactivatedIps.get("Owner2").contains("2.2.2.2"));
    }

    @Test
    void testParseDate_valid() {
        LocalDateTime dt = CsvUtils.parseDate("05/01/2025 08:00:00 AM");
        assertEquals(2025, dt.getYear());
        assertEquals(5, dt.getMonthValue());
        assertEquals(1, dt.getDayOfMonth());
        assertEquals(8, dt.getHour());
        assertEquals(0, dt.getMinute());
    }

    @Test
    void testParseDate_invalid_throws() {
        assertThrows(Exception.class, () -> CsvUtils.parseDate("not a date"));
    }
}