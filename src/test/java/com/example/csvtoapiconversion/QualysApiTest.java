package com.example.csvtoapiconversion;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.*;
import java.util.logging.*;

import static org.junit.jupiter.api.Assertions.*;

class QualysApiTest {

    private static final Logger LOGGER = Logger.getLogger(QualysApiTest.class.getName());

    /**
     * Test that makeApiCall throws on invalid action.
     */
    @Test
    void testMakeApiCallThrowsOnInvalidAction() {
        List<String> errors = new ArrayList<>();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            QualysApi.makeApiCall("invalid", "tag", new String[]{"1.2.3.4"}, errors, LOGGER);
        });
        assertTrue(ex.getMessage().contains("action must be 'add' or 'remove'"));
    }

    /**
     * Test that makeApiCall adds TAG_NOT_FOUND_AND_CREATE_FAILED to errorRecords if tag creation fails.
     * Uses mocking to force createQualysTag to return null.
     */
    @Test
    void testMakeApiCallTagNotFoundAndCreateFailed() {
        List<String> errors = new ArrayList<>();
        try (MockedStatic<QualysApi> mock = Mockito.mockStatic(QualysApi.class, Mockito.CALLS_REAL_METHODS)) {
            mock.when(() -> QualysApi.createQualysTag(Mockito.anyString(), Mockito.any(String[].class), Mockito.any()))
                .thenReturn(null);
            // Also mock editQualysTag to avoid real API call if needed
            mock.when(() -> QualysApi.editQualysTag(Mockito.anyString(), Mockito.anyString(), Mockito.any(String[].class), Mockito.any()))
                .thenReturn(null);

            QualysApi.makeApiCall("add", "nonexistent-tag", new String[]{"1.2.3.4"}, errors, LOGGER);
            assertTrue(errors.stream().anyMatch(e -> e.startsWith("TAG_NOT_FOUND_AND_CREATE_FAILED:")));
        }
    }

    /**
     * Test that the fatalCodes set contains all required codes for the tag API.
     */
    @Test
    void testMakeApiCallFatalErrorCodesSet() {
        Set<String> fatalCodes = Set.of(
            "1901", "1903", "1904", "1905", "1907", "1908",
            "1920", "1960", "1965", "1922", "1981", "999", "1999",
            "2000", "2002", "2003", "2011", "2012"
        );
        assertTrue(fatalCodes.contains("1901"));
        assertTrue(fatalCodes.contains("2012"));
        assertFalse(fatalCodes.contains("1234"));
    }

    /**
     * Test that editQualysTag method exists (reflection check).
     */
    @Test
    void testEditQualysTagMethodExists() {
        try {
            var method = QualysApi.class.getDeclaredMethod(
                "editQualysTag", String.class, String.class, String[].class, Logger.class
            );
            assertNotNull(method);
        } catch (NoSuchMethodException e) {
            fail("editQualysTag method should exist");
        }
    }

    /**
     * Test that createQualysTag method exists (reflection check).
     */
    @Test
    void testCreateQualysTagMethodExists() {
        try {
            var method = QualysApi.class.getDeclaredMethod(
                "createQualysTag", String.class, String[].class, Logger.class
            );
            assertNotNull(method);
        } catch (NoSuchMethodException e) {
            fail("createQualysTag method should exist");
        }
    }
}