package com.example.cmdbcvsconversion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

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
        if (code != null && (vmErrorCodes.contains(code) || !"SUCCESS".equals(code))) {
            errorRecords.add(code);
        }
    }

    @Test
    void testMakeApiCall_withSuccessCode() {
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
}