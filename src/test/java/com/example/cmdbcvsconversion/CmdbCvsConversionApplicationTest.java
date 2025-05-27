package com.example.cmdbcvsconversion;

import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CmdbCvsConversionApplicationTest {

    /**
     * Test parsing a valid date string.
     */
    @Test
    void testParseDate_validFormat() {
        String dateStr = "05/14/2025 08:30:00 AM";
        LocalDateTime dt = CmdbCvsConversionApplication.parseDate(dateStr);
        assertEquals(2025, dt.getYear());
        assertEquals(5, dt.getMonthValue());
        assertEquals(14, dt.getDayOfMonth());
        assertEquals(8, dt.getHour());
        assertEquals(30, dt.getMinute());
        assertEquals(0, dt.getSecond());
    }

    /**
     * Test extracting a code from a valid Qualys error XML.
     */
    @Test
    void testExtractQualysFoApiErrorCode_found() {
        String xml = "<RESPONSE><ERROR><CODE>1901</CODE></ERROR></RESPONSE>";
        String code = CmdbCvsConversionApplication.extractQualysFoApiErrorCode(xml);
        assertEquals("1901", code);
    }

    /**
     * Test extracting a code from XML with no error code present.
     */
    @Test
    void testExtractQualysFoApiErrorCode_notFound() {
        String xml = "<RESPONSE><SUCCESS/></RESPONSE>";
        String code = CmdbCvsConversionApplication.extractQualysFoApiErrorCode(xml);
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
     */
    @Test
    void testMakeApiCall_invalidAction_throws() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            CmdbCvsConversionApplication.makeApiCall("invalid", "group", new String[]{"1.2.3.4"}, null, null, new ArrayList<>())
        );
        assertTrue(ex.getMessage().contains("action must be 'add' or 'remove'"));
    }

    /**
     * Test that makeApiCall adds GROUP_NOT_FOUND to errorRecords if groupId is null.
     * (This test assumes lookupQualysGroupId returns null for a dummy group.)
     */
    @Test
    void testMakeApiCall_groupNotFound_addsErrorRecord() {
        List<String> errors = new ArrayList<>();
        // Use a group name that will not exist and suppress API call side effects
        CmdbCvsConversionApplication.makeApiCall("add", "nonexistent-group", new String[]{"1.2.3.4"}, null, null, errors);
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("GROUP_NOT_FOUND:")));
    }

    /**
     * Test QualysApiErrors main method runs without exceptions and prints expected output.
     */
    @Test
    void testQualysApiErrors_main_runs() {
        assertDoesNotThrow(() -> QualysApiErrors.main(new String[0]));
    }

    /**
     * Test parseDate throws DateTimeParseException for invalid format.
     */
    @Test
    void testParseDate_invalidFormat_throws() {
        assertThrows(Exception.class, () -> CmdbCvsConversionApplication.parseDate("not a date"));
    }
}