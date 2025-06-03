package com.example.csvtoapiconversion;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;

/**
 * Main application class for converting a CMDB CSV file to Qualys asset group API actions.
 * <p>
 * Features:
 * <ul>
 *   <li>Reads a CSV file and builds maps of owners/contacts to active and deactivated IPs.</li>
 *   <li>Optionally filters records by a start timestamp.</li>
 *   <li>Makes Qualys API calls to add/remove IPs from asset groups (unless suppressed).</li>
 *   <li>Logs all summary output and errors to both the console and a log file.</li>
 *   <li>Writes the completion timestamp to CsvToApiConversion.txt in the project root.</li>
 * </ul>
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
     * Entry point: parses arguments, processes CSV, manages API calls, logging, and writes completion timestamp.
     * @param args Command-line arguments: [csvPath] [startTimestamp] [suppressApiCall]
     * @throws Exception if an error occurs during processing
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
            config.getCsvPath(), config.getStartTimestamp(),
            ownerToActiveIps, contactToActiveIps,
            ownerToDeactivatedIps, contactToDeactivatedIps
        );

        // Process removals (deactivated IPs) before additions (active IPs)
        processRemovals(ownerToDeactivatedIps, "owner", config.isSuppressApiCall(), errorRecords);
        processRemovals(contactToDeactivatedIps, "contact", config.isSuppressApiCall(), errorRecords);
        processAdditions(ownerToActiveIps, "owner", config.isSuppressApiCall(), errorRecords);
        processAdditions(contactToActiveIps, "contact", config.isSuppressApiCall(), errorRecords);

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

        // Write the current timestamp to CsvToApiConversion.txt in the project root
        try {
            String timestamp = java.time.LocalDateTime.now().toString();
            Path outputPath = Paths.get("CsvToApiConversion.txt");
            Files.writeString(outputPath, timestamp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info(String.format("Wrote completion timestamp to CsvToApiConversion.txt: %s", timestamp));
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to write completion timestamp: " + e.getMessage());
        }
    }

    /**
     * For each group (owner/contact), remove IPs that are deactivated.
     * If suppressApiCall is true, only print what would be done.
     *
     * @param groupToIps Map of group name to set of IPs to remove
     * @param groupType  "owner" or "contact"
     * @param suppressApiCall If true, do not make API calls (dry run)
     * @param errorRecords List to collect error records
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
     *
     * @param groupToIps Map of group name to set of IPs to add
     * @param groupType  "owner" or "contact"
     * @param suppressApiCall If true, do not make API calls (dry run)
     * @param errorRecords List to collect error records
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
     * If start timestamp is not provided as an argument, attempts to read it from CsvToApiConversion.txt.
     * @param args Command-line arguments
     * @return ArgsConfig object with parsed values
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

        // Try to get startTimestamp from args or from CsvToApiConversion.txt
        if (args.length >= 2 && args[1] != null && !args[1].isBlank()) {
            try {
                startTimestamp = CsvUtils.parseDate(args[1]);
            } catch (Exception e) {
                LOGGER.warning("Invalid start timestamp format. Expected: MM/dd/yyyy hh:mm:ss a");
                startTimestamp = null;
            }
        } else {
            // Try to read from CsvToApiConversion.txt if present
            Path tsFile = Paths.get("CsvToApiConversion.txt");
            if (Files.exists(tsFile)) {
                try {
                    String tsString = Files.readString(tsFile).trim();
                    if (!tsString.isEmpty()) {
                        // Try parsing as ISO_LOCAL_DATE_TIME first (default write format)
                        try {
                            startTimestamp = LocalDateTime.parse(tsString);
                        } catch (Exception e) {
                            // Fallback: try parsing as MM/dd/yyyy hh:mm:ss a
                            try {
                                startTimestamp = CsvUtils.parseDate(tsString);
                            } catch (Exception ex) {
                                LOGGER.warning("Could not parse timestamp from CsvToApiConversion.txt: " + ex.getMessage());
                            }
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warning("Could not read CsvToApiConversion.txt for start timestamp: " + e.getMessage());
                }
            }
        }

        if (args.length >= 3 && args[2] != null && !args[2].isBlank()) {
            suppressApiCall = Boolean.parseBoolean(args[2]);
        }

        return new ArgsConfig(csvPath, startTimestamp, suppressApiCall);
    }

    /**
     * Simple config holder for parsed arguments.
     * Provides getters for csvPath, startTimestamp, and suppressApiCall.
     */
    public static class ArgsConfig {
        private final Path csvPath;
        private final LocalDateTime startTimestamp;
        private final boolean suppressApiCall;

        ArgsConfig(Path csvPath, LocalDateTime startTimestamp, boolean suppressApiCall) {
            this.csvPath = csvPath;
            this.startTimestamp = startTimestamp;
            this.suppressApiCall = suppressApiCall;
        }

        public Path getCsvPath() {
            return csvPath;
        }

        public LocalDateTime getStartTimestamp() {
            return startTimestamp;
        }

        public boolean isSuppressApiCall() {
            return suppressApiCall;
        }
    }
}