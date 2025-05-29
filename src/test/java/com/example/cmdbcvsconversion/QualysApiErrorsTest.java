package com.example.cmdbcvsconversion;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class QualysApiErrorsTest {

    @Test
    void testGetDescriptionByCode_known() {
        assertEquals("Unrecognized parameter(s)", QualysApiErrors.getDescriptionByCode("1901"));
    }

    @Test
    void testGetDescriptionByCode_unknown() {
        assertEquals("Unknown error code", QualysApiErrors.getDescriptionByCode("9999"));
    }

    @Test
    void testGetCodeByDescription_known() {
        assertEquals("1901", QualysApiErrors.getCodeByDescription("Unrecognized parameter(s)"));
    }

    @Test
    void testGetCodeByDescription_caseInsensitive() {
        assertEquals("1901", QualysApiErrors.getCodeByDescription("unrecognized parameter(s)"));
    }

    @Test
    void testGetCodeByDescription_unknown() {
        assertEquals("Unknown description", QualysApiErrors.getCodeByDescription("Not a real error"));
    }

    @Test
    void testExtractQualysFoApiErrorCode_found() {
        String xml = "<RESPONSE><ERROR><CODE>1901</CODE></ERROR></RESPONSE>";
        assertEquals("1901", QualysApiErrors.extractQualysFoApiErrorCode(xml));
    }

    @Test
    void testExtractQualysFoApiErrorCode_notFound() {
        String xml = "<RESPONSE><SUCCESS/></RESPONSE>";
        assertNull(QualysApiErrors.extractQualysFoApiErrorCode(xml));
    }

    @Test
    void testMain_runsWithoutException() {
        assertDoesNotThrow(() -> QualysApiErrors.main(new String[0]));
    }
}