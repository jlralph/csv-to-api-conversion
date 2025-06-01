package com.example.csvtoapiconversion;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;

/**
 * Main application class for CMDB CSV to Qualys asset group conversion.
 * - Reads a CSV file and builds maps of owners/contacts to active and deactivated IPs.
 * - Optionally filters records by a start timestamp.
 * - Makes Qualys API calls to add/remove IPs from asset groups (unless suppressed).
 * - Logs all summary output and errors to both the console and a log file.
 */
public class CsvToApiConversionApplication {

    // Logger setup for both file and console output
    private static final Logger LOGGER = Logger.getLogger(CsvToApiConversionApplication.class.getName());
    static {
        try {
            FileHandler fileHandler = new FileHandler("csv-to-api-conversion.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setUseParentHandlers(true);
        } catch (IOException e) {
            System.err.println("Failed to set up file logger: " + e.getMessage());
        }
    }

    /**
     * Entry point: parses arguments, processes CSV, and manages API calls and logging.
     */
    public static void main(String[] args) throws Exception {
        ArgsConfig config = parseArgs(args);

        List<String> errorRecords = new ArrayList<>();
        Map<String, Set<String>> ownerToActiveIps = new HashMap<>();
        Map<String, Set<String>> contactToActiveIps = new HashMap<>();
        Map<String, Set<String>> ownerToDeactivatedIps = new HashMap<>();
        Map<String, Set<String>> contactToDeactivatedIps = new HashMap<>();

        // Parse CSV and build maps for owner/contact to active/deactivated IPs
        CsvUtils.processCsv(
            config.csvPath, config.startTimestamp,
            ownerToActiveIps, contactToActiveIps,
            ownerToDeactivatedIps, contactToDeactivatedIps
        );

        // Process removals (deactivated IPs) before additions (active IPs)
        processRemovals(ownerToDeactivatedIps, "owner", config.suppressApiCall, errorRecords);
        processRemovals(contactToDeactivatedIps, "contact", config.suppressApiCall, errorRecords);
        processAdditions(ownerToActiveIps, "owner", config.suppressApiCall, errorRecords);
        processAdditions(contactToActiveIps, "contact", config.suppressApiCall, errorRecords);

        // Output error records and summary maps to console and logger
        System.out.println("Error Records: " + errorRecords);

        LOGGER.info("Owner to Active IPs:");
        ownerToActiveIps.forEach((k, v) -> LOGGER.info("Owner: " + k + " -> IPs: " + v));
        LOGGER.info("Contact to Active IPs:");
        contactToActiveIps.forEach((k, v) -> LOGGER.info("Contact: " + k + " -> IPs: " + v));
        LOGGER.info("Owner to Deactivated IPs:");
        ownerToDeactivatedIps.forEach((k, v) -> LOGGER.info("Owner: " + k + " -> Deactivated IPs: " + v));
        LOGGER.info("Contact to Deactivated IPs:");
        contactToDeactivatedIps.forEach((k, v) -> LOGGER.info("Contact: " + k + " -> Deactivated IPs: " + v));
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info(String.format("Error Records: %s", errorRecords));
        }
    }

    /**
     * For each group (owner/contact), remove IPs that are deactivated.
     * If suppressApiCall is true, only print what would be done.
     */
    private static void processRemovals(Map<String, Set<String>> groupToIps, String groupType, boolean suppressApiCall, List<String> errorRecords) {
        for (Map.Entry<String, Set<String>> entry : groupToIps.entrySet()) {
            String group = entry.getKey();
            Set<String> ips = entry.getValue();
            if (!suppressApiCall) {
                QualysApi.makeApiCall("remove", group, ips.toArray(new String[0]), errorRecords, LOGGER);
            } else {
                System.out.println("[DRY RUN] Would remove IPs " + ips + " from " + groupType + " group: " + group);
            }
        }
    }

    /**
     * For each group (owner/contact), add IPs that are active.
     * If suppressApiCall is true, only print what would be done.
     */
    private static void processAdditions(Map<String, Set<String>> groupToIps, String groupType, boolean suppressApiCall, List<String> errorRecords) {
        for (Map.Entry<String, Set<String>> entry : groupToIps.entrySet()) {
            String group = entry.getKey();
            Set<String> ips = entry.getValue();
            if (!suppressApiCall) {
                QualysApi.makeApiCall("add", group, ips.toArray(new String[0]), errorRecords, LOGGER);
            } else {
                System.out.println("[DRY RUN] Would add IPs " + ips + " to " + groupType + " group: " + group);
            }
        }
    }

    /**
     * Parse command-line arguments for CSV path, start timestamp, and suppressApiCall flag.
     */
    private static ArgsConfig parseArgs(String[] args) {
        Path csvPath;
        LocalDateTime startTimestamp = null;
        boolean suppressApiCall = false;

        if (args.length < 1) {
            csvPath = Paths.get("src/main/resources/sample.csv");
        } else {
            csvPath = Paths.get(args[0]);
        }

        if (args.length >= 2) {
            try {
                startTimestamp = CsvUtils.parseDate(args[1]);
            } catch (Exception e) {
                System.err.println("Invalid start timestamp format. Expected: MM/dd/yyyy hh:mm:ss a");
                startTimestamp = null;
            }
        }

        if (args.length >= 3 && args[2] != null && !args[2].isBlank()) {
            suppressApiCall = Boolean.parseBoolean(args[2]);
        }

        return new ArgsConfig(csvPath, startTimestamp, suppressApiCall);
    }

    /**
     * Simple config holder for parsed arguments.
     */
    private static class ArgsConfig {
        Path csvPath;
        LocalDateTime startTimestamp;
        boolean suppressApiCall;

        ArgsConfig(Path csvPath, LocalDateTime startTimestamp, boolean suppressApiCall) {
            this.csvPath = csvPath;
            this.startTimestamp = startTimestamp;
            this.suppressApiCall = suppressApiCall;
        }
    }
}