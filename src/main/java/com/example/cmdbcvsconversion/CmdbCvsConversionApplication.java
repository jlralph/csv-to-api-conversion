package com.example.cmdbcvsconversion;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;

public class CmdbCvsConversionApplication {

    private static final Logger LOGGER = Logger.getLogger(CmdbCvsConversionApplication.class.getName());
    static {
        try {
            FileHandler fileHandler = new FileHandler("cmdb-cvs-conversion.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setUseParentHandlers(true);
        } catch (IOException e) {
            System.err.println("Failed to set up file logger: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        ArgsConfig config = parseArgs(args);

        List<String> errorRecords = new ArrayList<>();
        Map<String, Set<String>> ownerToActiveIps = new HashMap<>();
        Map<String, Set<String>> contactToActiveIps = new HashMap<>();
        Map<String, Set<String>> ownerToDeactivatedIps = new HashMap<>();
        Map<String, Set<String>> contactToDeactivatedIps = new HashMap<>();

        CsvUtils.processCsv(
            config.csvPath, config.startTimestamp,
            ownerToActiveIps, contactToActiveIps,
            ownerToDeactivatedIps, contactToDeactivatedIps
        );

        processRemovals(ownerToDeactivatedIps, "owner", config.suppressApiCall, errorRecords);
        processRemovals(contactToDeactivatedIps, "contact", config.suppressApiCall, errorRecords);
        processAdditions(ownerToActiveIps, "owner", config.suppressApiCall, errorRecords);
        processAdditions(contactToActiveIps, "contact", config.suppressApiCall, errorRecords);

        System.out.println("Error Records: " + errorRecords);

        LOGGER.info("Owner to Active IPs:");
        ownerToActiveIps.forEach((k, v) -> LOGGER.info("Owner: " + k + " -> IPs: " + v));
        LOGGER.info("Contact to Active IPs:");
        contactToActiveIps.forEach((k, v) -> LOGGER.info("Contact: " + k + " -> IPs: " + v));
        LOGGER.info("Owner to Deactivated IPs:");
        ownerToDeactivatedIps.forEach((k, v) -> LOGGER.info("Owner: " + k + " -> Deactivated IPs: " + v));
        LOGGER.info("Contact to Deactivated IPs:");
        contactToDeactivatedIps.forEach((k, v) -> LOGGER.info("Contact: " + k + " -> Deactivated IPs: " + v));
        LOGGER.info("Error Records: " + errorRecords);
    }

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