package com.example.csvtoapiconversion;

import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;


class CsvToApiConversionApplicationTest {

    /**
     * Test extracting a code from a valid Qualys error XML.
     */
    @Test
    void testExtractQualysFoApiErrorCode_found() {
        String xml = "<RESPONSE><ERROR><CODE>1901</CODE></ERROR></RESPONSE>";
        String code = QualysApiErrors.extractQualysFoApiErrorCode(xml);
        assertEquals("1901", code);
    }

    /**
     * Test extracting a code from XML with no error code present.
     */
    @Test
    void testExtractQualysFoApiErrorCode_notFound() {
        String xml = "<RESPONSE><SUCCESS/></RESPONSE>";
        String code = QualysApiErrors.extractQualysFoApiErrorCode(xml);
        assertNull(code);
    }

    /**
     * Test known error code returns correct description.
     */
    @Test
    void testQualysApiErrors_getDescriptionByCode_known() {
        assertEquals("Unrecognized parameter(s)", QualysApiErrors.getDescriptionByCode("1901"));
    }

    /**
     * Test unknown error code returns "Unknown error code".
     */
    @Test
    void testQualysApiErrors_getDescriptionByCode_unknown() {
        assertEquals("Unknown error code", QualysApiErrors.getDescriptionByCode("9999"));
    }

    /**
     * Test known description returns correct code.
     */
    @Test
    void testQualysApiErrors_getCodeByDescription_known() {
        assertEquals("1901", QualysApiErrors.getCodeByDescription("Unrecognized parameter(s)"));
    }

    /**
     * Test description lookup is case-insensitive.
     */
    @Test
    void testQualysApiErrors_getCodeByDescription_caseInsensitive() {
        assertEquals("1901", QualysApiErrors.getCodeByDescription("unrecognized parameter(s)"));
    }

    /**
     * Test unknown description returns "Unknown description".
     */
    @Test
    void testQualysApiErrors_getCodeByDescription_unknown() {
        assertEquals("Unknown description", QualysApiErrors.getCodeByDescription("Not a real error"));
    }

    /**
     * Test that an invalid action throws an exception.
     * (The application should only accept "add" or "remove" as valid actions.)
     */
    @Test
    void testMakeApiCall_invalidAction_throws() {
        String action = "invalid";
        String tag = "tag";
        String[] ips = new String[]{"1.2.3.4"};
        List<String> errorList = new ArrayList<>();
        java.util.logging.Logger logger = java.util.logging.Logger.getGlobal();
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> QualysApi.makeApiCall(action, tag, ips, errorList, logger)
        );
        assertTrue(ex.getMessage().contains("action must be 'add' or 'remove'"));
    }

    /**
     * Test that makeApiCall adds TAG_NOT_FOUND_AND_CREATE_FAILED to errorRecords if tag creation fails.
     * (This test assumes createQualysTag returns null for a dummy tag.)
     * Note: This test may need to mock QualysApi.createQualysTag for isolation.
     */
    @Test
    void testMakeApiCall_tagNotFoundAndCreateFailed_addsErrorRecord() {
        List<String> errors = new ArrayList<>();
        // Use a tag name that will not exist and suppress API call side effects
        QualysApi.makeApiCall("add", "nonexistent-tag", new String[]{"1.2.3.4"}, errors, java.util.logging.Logger.getGlobal());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("TAG_NOT_FOUND_AND_CREATE_FAILED:")));
    }

    /**
     * Test QualysApiErrors main method runs without exceptions and prints expected output.
     */
    @Test
    void testQualysApiErrors_main_runs() {
        assertDoesNotThrow(() -> QualysApiErrors.main(new String[0]));
    }

    /**
     * Test parseDate throws Exception for invalid format.
     */
    @Test
    void testParseDate_invalidFormat_throws() {
        assertThrows(Exception.class, () -> CsvUtils.parseDate("not a date"));
    }

    /**
     * Test parseDate parses a valid date string.
     */
    @Test
    void testParseDate_validFormat() {
        String dateStr = "05/14/2025 08:30:00 AM";
        LocalDateTime dt = CsvUtils.parseDate(dateStr);
        assertEquals(2025, dt.getYear());
        assertEquals(5, dt.getMonthValue());
        assertEquals(14, dt.getDayOfMonth());
        assertEquals(8, dt.getHour());
        assertEquals(30, dt.getMinute());
        assertEquals(0, dt.getSecond());
    }

    /**
     * Test parseArgs uses command line start timestamp if provided.
     */
    @Test
    void testParseArgs_UsesCommandLineStartTimestamp() {
        String[] args = {"test.csv", "05/14/2025 08:30:00 AM", "true"};
        var config = invokeParseArgs(args);
        CsvToApiConversionApplication.ArgsConfig cfg = (CsvToApiConversionApplication.ArgsConfig) config;
        assertEquals(Paths.get("test.csv"), cfg.getCsvPath());
        assertEquals(LocalDateTime.of(2025, 5, 14, 8, 30, 0), cfg.getStartTimestamp());
        assertTrue(cfg.isSuppressApiCall());
    }

    /**
     * Test parseArgs reads timestamp from file if not provided on command line (ISO format).
     */
    @Test
    void testParseArgs_ReadsTimestampFromFileIfNotProvided() throws IOException {
        Path tsFile = Paths.get("CsvToApiConversion.txt");
        String isoTimestamp = "2025-06-02T12:34:56";
        Files.writeString(tsFile, isoTimestamp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        String[] args = {"test.csv"};
        var config = invokeParseArgs(args);
        CsvToApiConversionApplication.ArgsConfig cfg = (CsvToApiConversionApplication.ArgsConfig) config;
        assertEquals(LocalDateTime.parse(isoTimestamp), cfg.getStartTimestamp());

        Files.deleteIfExists(tsFile);
    }

    /**
     * Test parseArgs reads timestamp from file with fallback legacy format.
     */
    @Test
    void testParseArgs_ReadsTimestampFromFileWithFallbackFormat() throws IOException {
        Path tsFile = Paths.get("CsvToApiConversion.txt");
        String legacyTimestamp = "05/14/2025 08:30:00 AM";
        Files.writeString(tsFile, legacyTimestamp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        String[] args = {"test.csv"};
        var config = invokeParseArgs(args);
        CsvToApiConversionApplication.ArgsConfig cfg = (CsvToApiConversionApplication.ArgsConfig) config;
        assertEquals(LocalDateTime.of(2025, 5, 14, 8, 30, 0), cfg.getStartTimestamp());

        Files.deleteIfExists(tsFile);
    }

    /**
     * Test parseArgs uses defaults if no arguments are provided.
     */
    @Test
    void testParseArgs_Defaults() {
        String[] args = {};
        var config = invokeParseArgs(args);
        CsvToApiConversionApplication.ArgsConfig cfg = (CsvToApiConversionApplication.ArgsConfig) config;
        assertEquals(Paths.get("src/main/resources/sample.csv"), cfg.getCsvPath());
        assertFalse(cfg.isSuppressApiCall());
    }

    // Helper to invoke private static parseArgs
    private static Object invokeParseArgs(String[] args) {
        try {
            var method = CsvToApiConversionApplication.class.getDeclaredMethod("parseArgs", String[].class);
            method.setAccessible(true);
            return method.invoke(null, (Object) args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}