package com.example.cmdbcvsconversion;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.logging.*;

import static org.junit.jupiter.api.Assertions.*;

class QualysApiTest {

    @Test
    void testMakeApiCall_invalidAction_throws() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            QualysApi.makeApiCall("invalid", "group", new String[]{"1.2.3.4"}, new ArrayList<>(), Logger.getGlobal())
        );
        assertTrue(ex.getMessage().contains("action must be 'add' or 'remove'"));
    }

    @Test
    void testMakeApiCall_groupNotFound_addsErrorRecord() {
        List<String> errors = new ArrayList<>();
        // Use a group name that will not exist and suppress API call side effects
        QualysApi.makeApiCall("add", "nonexistent-group", new String[]{"1.2.3.4"}, errors, Logger.getGlobal());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("GROUP_NOT_FOUND:")));
    }
}