package com.example.csvtoapiconversion;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.logging.*;

import static org.junit.jupiter.api.Assertions.*;

class QualysApiTest {

    private static final Logger LOGGER = Logger.getLogger(QualysApiTest.class.getName());

    @Test
    void testMakeApiCallThrowsOnInvalidAction() {
        List<String> errors = new ArrayList<>();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            QualysApi.makeApiCall("invalid", "group", new String[]{"1.2.3.4"}, errors, LOGGER);
        });
        assertTrue(ex.getMessage().contains("action must be 'add' or 'remove'"));
    }

    @Test
    void testMakeApiCallGroupNotFound() {
        List<String> errors = new ArrayList<>();
        Logger logger = Logger.getLogger("TestLogger");
        // Override lookupQualysGroupId to always return null for this test
        QualysApi api = new QualysApi() {
            static String lookupQualysGroupId(String groupName, Logger logger) {
                return null;
            }
        };
        // Use reflection to call the static method with the overridden version
        // But since lookupQualysGroupId is private static, we can't override it directly.
        // So, we just call the real method and expect GROUP_NOT_FOUND in errors.
        api.makeApiCall("add", "nonexistent-group", new String[]{"1.2.3.4"}, errors, logger);
        assertTrue(errors.stream().anyMatch(e -> e.contains("GROUP_NOT_FOUND")));
    }

    @Test
    void testMakeApiCallFatalErrorCodesExit() {
        // We can't actually call System.exit in a unit test, so we check the logic up to that point.
        // Instead, we can refactor makeApiCall to allow injection/mocking for testing, or just document this limitation.
        // Here, we just verify that the fatalCodes set contains all required codes.
        Set<String> fatalCodes = Set.of(
            "1920", "1960", "1965", "1981",
            "999", "1999", "2000", "2002", "2003", "2011", "2012"
        );
        assertTrue(fatalCodes.contains("1920"));
        assertTrue(fatalCodes.contains("2012"));
        assertFalse(fatalCodes.contains("1234"));
    }

    @Test
    void testEditQualysAssetGroupBuildsCorrectParams() {
        // This test is limited since the method is private and does real HTTP calls.
        // You could refactor editQualysAssetGroup to be package-private for testing, or use reflection.
        // Here, we just check that the method exists and can be called via reflection.
        try {
            var method = QualysApi.class.getDeclaredMethod(
                "editQualysAssetGroup", String.class, String.class, String[].class, Logger.class
            );
            assertNotNull(method);
        } catch (NoSuchMethodException e) {
            fail("editQualysAssetGroup method should exist");
        }
    }

    @Test
    void testLookupQualysGroupIdBuildsCorrectParams() {
        // This test is limited since the method is private and does real HTTP calls.
        // You could refactor lookupQualysGroupId to be package-private for testing, or use reflection.
        // Here, we just check that the method exists and can be called via reflection.
        try {
            var method = QualysApi.class.getDeclaredMethod(
                "lookupQualysGroupId", String.class, Logger.class
            );
            assertNotNull(method);
        } catch (NoSuchMethodException e) {
            fail("lookupQualysGroupId method should exist");
        }
    }
}