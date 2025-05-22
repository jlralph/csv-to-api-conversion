package com.example.cmdbcvsconversion;

import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CmdbCvsConversionApplicationTest {

    /**
     * Mock version of makeApiCall for testing.
     * Simulates API responses by returning provided JSON strings.
     */
    private static void mockMakeApiCall(
            String jsonResponse,
            List<String> vmErrorCodes,
            List<String> errorRecords
    ) {
        // Extract "code" field from JSON response (simple string search)
        String response = jsonResponse;
        String code = null;
        int codeIdx = response.indexOf("\"code\"");
        if (codeIdx != -1) {
            int colonIdx = response.indexOf(":", codeIdx);
            if (colonIdx != -1) {
                int start = colonIdx + 1;
                while (start < response.length() && !Character.isDigit(response.charAt(start))) start++;
                int end = start;
                while (end < response.length() && Character.isDigit(response.charAt(end))) end++;
                if (start < end) {
                    code = response.substring(start, end);
                }
            }
        }
        // Add code to errorRecords if it's in the known error codes or not "SUCCESS"
        if (code != null && (vmErrorCodes.contains(code) || !"SUCCESS".equals(code))) {
            errorRecords.add(code);
        }
    }

    @Test
    void testMakeApiCall_withSuccessCode() {
        // Test that SUCCESS code does not add to errorRecords
        List<String> vmErrorCodes = Arrays.asList(
                "1901", "1903", "1904", "1905", "1907", "1908", "1920", "1960", "1965",
                "1922", "1981", "999", "1999", "2000", "2002", "2003", "2011", "2012"
        );
        List<String> errorRecords = new ArrayList<>();

        // Simulate a successful API response
        String jsonResponse = "{\"code\":SUCCESS,\"message\":\"SUCCESS\"}";

        mockMakeApiCall(jsonResponse, vmErrorCodes, errorRecords);

        assertTrue(errorRecords.isEmpty(), "No error codes should be recorded for SUCCESS");
    }

    @Test
    void testMakeApiCall_withErrorCode() {
        // Test that a known error code is added to errorRecords
        List<String> vmErrorCodes = Arrays.asList(
                "1901", "1903", "1904", "1905", "1907", "1908", "1920", "1960", "1965",
                "1922", "1981", "999", "1999", "2000", "2002", "2003", "2011", "2012"
        );
        List<String> errorRecords = new ArrayList<>();

        // Simulate an error API response
        String jsonResponse = "{\"code\":1901,\"message\":\"Unrecognized parameter(s)\"}";

        mockMakeApiCall(jsonResponse, vmErrorCodes, errorRecords);

        assertEquals(1, errorRecords.size(), "Error code should be recorded");
        assertEquals("1901", errorRecords.get(0));
    }

    @Test
    void testMakeApiCall_withUnknownNonSuccessCode() {
        // Test that an unknown error code (not in known codes and not SUCCESS) is still added
        List<String> vmErrorCodes = Arrays.asList(
                "1901", "1903", "1904", "1905", "1907", "1908", "1920", "1960", "1965",
                "1922", "1981", "999", "1999", "2000", "2002", "2003", "2011", "2012"
        );
        List<String> errorRecords = new ArrayList<>();

        // Simulate an unknown error code (not in errorCodes and not "SUCCESS")
        String jsonResponse = "{\"code\":1234,\"message\":\"Some error\"}";

        mockMakeApiCall(jsonResponse, vmErrorCodes, errorRecords);

        assertEquals(1, errorRecords.size(), "Unknown non-SUCCESS code should be recorded");
        assertEquals("1234", errorRecords.get(0));
    }

    @Test
    void testParseDate_validFormat() {
        // Test parsing a valid date string
        String dateStr = "05/14/2025 08:30:00 AM";
        LocalDateTime dt = CmdbCvsConversionApplication.parseDate(dateStr);
        assertEquals(2025, dt.getYear());
        assertEquals(5, dt.getMonthValue());
        assertEquals(14, dt.getDayOfMonth());
        assertEquals(8, dt.getHour());
        assertEquals(30, dt.getMinute());
        assertEquals(0, dt.getSecond());
    }

    @Test
    void testExtractQualysFoApiErrorCode_found() {
        // Test extracting a code from a valid Qualys error XML
        String xml = "<RESPONSE><ERROR><CODE>1901</CODE></ERROR></RESPONSE>";
        String code = CmdbCvsConversionApplication.extractQualysFoApiErrorCode(xml);
        assertEquals("1901", code);
    }

    @Test
    void testExtractQualysFoApiErrorCode_notFound() {
        // Test extracting a code from XML with no error code present
        String xml = "<RESPONSE><SUCCESS/></RESPONSE>";
        String code = CmdbCvsConversionApplication.extractQualysFoApiErrorCode(xml);
        assertNull(code);
    }

    @Test
    void testQualysApiErrors_getDescriptionByCode_known() {
        // Test known error code returns correct description
        assertEquals("Unrecognized parameter(s)", QualysApiErrors.getDescriptionByCode("1901"));
    }

    @Test
    void testQualysApiErrors_getDescriptionByCode_unknown() {
        // Test unknown error code returns "Unknown error code"
        assertEquals("Unknown error code", QualysApiErrors.getDescriptionByCode("9999"));
    }

    @Test
    void testQualysApiErrors_getCodeByDescription_known() {
        // Test known description returns correct code
        assertEquals("1901", QualysApiErrors.getCodeByDescription("Unrecognized parameter(s)"));
    }

    @Test
    void testQualysApiErrors_getCodeByDescription_caseInsensitive() {
        // Test description lookup is case-insensitive
        assertEquals("1901", QualysApiErrors.getCodeByDescription("unrecognized parameter(s)"));
    }

    @Test
    void testQualysApiErrors_getCodeByDescription_unknown() {
        // Test unknown description returns "Unknown description"
        assertEquals("Unknown description", QualysApiErrors.getCodeByDescription("Not a real error"));
    }

    @Test
    void testMakeApiCall_invalidAction_throws() {
        // Test that an invalid action throws an exception
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            CmdbCvsConversionApplication.makeApiCall("invalid", "group", new String[]{"1.2.3.4"}, null, null, new ArrayList<>())
        );
        assertTrue(ex.getMessage().contains("action must be 'add' or 'remove'"));
    }

    @Test
    void testMakeApiCall_groupNotFound_addsErrorRecord() throws Exception {
        // Test that a missing group adds GROUP_NOT_FOUND to errorRecords
        List<String> errorRecords = new ArrayList<>();
        CmdbCvsConversionApplication.makeApiCall("add", "NonExistentGroup", new String[]{"1.2.3.4"}, null, null, errorRecords);
        assertTrue(errorRecords.stream().anyMatch(e -> e.contains("GROUP_NOT_FOUND")));
    }

    @Test
    void testMakeApiCall_errorCodeAddedToErrorRecords() throws Exception {
        // Test that a known error code is added to errorRecords (integration-like)
        List<String> errorRecords = new ArrayList<>();
        CmdbCvsConversionApplication.makeApiCall("add", "SomeGroup", new String[]{"1.2.3.4"}, null, null, errorRecords);
        // This test expects no error code "1901" unless the real API returns it
        assertFalse(errorRecords.stream().anyMatch(e -> e.contains("1901")));
    }
}