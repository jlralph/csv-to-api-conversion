package com.example.csvtoapiconversion;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class QualysApiErrorsTest {

    /**
     * Test that a known error code returns the correct description.
     */
    @Test
    void testGetDescriptionByCode_known() {
        assertEquals("Unrecognized parameter(s)", QualysApiErrors.getDescriptionByCode("1901"));
    }

    /**
     * Test that an unknown error code returns "Unknown error code".
     */
    @Test
    void testGetDescriptionByCode_unknown() {
        assertEquals("Unknown error code", QualysApiErrors.getDescriptionByCode("9999"));
    }

    /**
     * Test that a known description returns the correct error code.
     */
    @Test
    void testGetCodeByDescription_known() {
        assertEquals("1901", QualysApiErrors.getCodeByDescription("Unrecognized parameter(s)"));
    }

    /**
     * Test that description lookup is case-insensitive.
     */
    @Test
    void testGetCodeByDescription_caseInsensitive() {
        assertEquals("1901", QualysApiErrors.getCodeByDescription("unrecognized parameter(s)"));
    }

    /**
     * Test that an unknown description returns "Unknown description".
     */
    @Test
    void testGetCodeByDescription_unknown() {
        assertEquals("Unknown description", QualysApiErrors.getCodeByDescription("Not a real error"));
    }

    /**
     * Test extracting an error code from a valid Qualys error XML.
     */
    @Test
    void testExtractQualysFoApiErrorCode_found() {
        String xml = "<RESPONSE><ERROR><CODE>1901</CODE></ERROR></RESPONSE>";
        assertEquals("1901", QualysApiErrors.extractQualysFoApiErrorCode(xml));
    }

    /**
     * Test extracting an error code from XML with no error code present.
     */
    @Test
    void testExtractQualysFoApiErrorCode_notFound() {
        String xml = "<RESPONSE><SUCCESS/></RESPONSE>";
        assertNull(QualysApiErrors.extractQualysFoApiErrorCode(xml));
    }

    /**
     * Test that the main method runs without throwing exceptions.
     */
    @Test
    void testMain_runsWithoutException() {
        assertDoesNotThrow(() -> QualysApiErrors.main(new String[0]));
    }
}