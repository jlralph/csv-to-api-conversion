package com.example.csvtoapiconversion;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class CsvToApiConversionApplicationSmokeTest {

    @Test
    void testMain_runsWithoutException() {
        // This is a smoke test: just ensure main runs with default args and doesn't throw
        assertDoesNotThrow(() -> CsvToApiConversionApplication.main(new String[0]));
    }

    @Test
    void testMain_runsWithSampleCsv() {
        String[] args = {"src/main/resources/sample.csv"};
        assertDoesNotThrow(() -> CsvToApiConversionApplication.main(args));
    }

    @Test
    void testMain_runsWithSuppressApiCall() {
        String[] args = {"src/main/resources/sample.csv", "05/01/2025 08:00:00 AM", "true"};
        assertDoesNotThrow(() -> CsvToApiConversionApplication.main(args));
    }
}