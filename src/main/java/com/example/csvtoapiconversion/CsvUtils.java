package com.example.csvtoapiconversion;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Utility class for processing the CSV file and building maps of owners/contacts to active and deactivated IPs.
 */
public class CsvUtils {

    /**
     * Reads the CSV file and populates the provided maps with active and deactivated IPs
     * for each owner and contact, based on the create and deactivated timestamps.
     *
     * Business logic:
     * - If a row's deactivated timestamp is empty, all IPs are considered "active" and
     *   are added to both the owner's and contact's active sets.
     * - If a row's deactivated timestamp is present, all IPs are considered "deactivated"
     *   and are added to both the owner's and contact's deactivated sets.
     * - If a startTimestamp is provided, only rows where either the create or deactivated
     *   timestamp is after or equal to the startTimestamp are included.
     *
     * @param csvPath Path to the CSV file
     * @param startTimestamp Optional filter for create/deactivated timestamps
     * @param ownerToActiveIps Output: owner → set of active IPs
     * @param contactToActiveIps Output: contact → set of active IPs
     * @param ownerToDeactivatedIps Output: owner → set of deactivated IPs
     * @param contactToDeactivatedIps Output: contact → set of deactivated IPs
     * @throws IOException if the file cannot be read
     */
    public static void processCsv(
            Path csvPath,
            LocalDateTime startTimestamp,
            Map<String, Set<String>> ownerToActiveIps,
            Map<String, Set<String>> contactToActiveIps,
            Map<String, Set<String>> ownerToDeactivatedIps,
            Map<String, Set<String>> contactToDeactivatedIps
    ) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                processCsvRow(
                    line,
                    startTimestamp,
                    ownerToActiveIps,
                    contactToActiveIps,
                    ownerToDeactivatedIps,
                    contactToDeactivatedIps
                );
            }
        }
    }

    /**
     * Processes a single row from the CSV file and updates the appropriate maps.
     * Skips invalid rows and applies the startTimestamp filter if provided.
     *
     * @param line The CSV row as a string
     * @param startTimestamp Optional filter for create/deactivated timestamps
     * @param ownerToActiveIps Output: owner → set of active IPs
     * @param contactToActiveIps Output: contact → set of active IPs
     * @param ownerToDeactivatedIps Output: owner → set of deactivated IPs
     * @param contactToDeactivatedIps Output: contact → set of deactivated IPs
     */
    private static void processCsvRow(
            String line,
            LocalDateTime startTimestamp,
            Map<String, Set<String>> ownerToActiveIps,
            Map<String, Set<String>> contactToActiveIps,
            Map<String, Set<String>> ownerToDeactivatedIps,
            Map<String, Set<String>> contactToDeactivatedIps
    ) {
        String[] cols = line.split(",", -1);
        if (cols.length < 6) return; // Skip invalid rows

        String assetName = cols[0].trim();
        String contact = cols[1].trim();
        String owner = cols[2].trim();

        // IPs are from index 3 up to (length - 2)
        String[] ips = Arrays.copyOfRange(cols, 3, cols.length - 2);
        for (int i = 0; i < ips.length; i++) ips[i] = ips[i].trim();

        String createTimestampStr = cols[cols.length - 2].trim();
        String deactivatedTimestampStr = cols[cols.length - 1].trim();

        LocalDateTime createTimestamp = createTimestampStr.isEmpty() ? null : parseDate(createTimestampStr);
        LocalDateTime deactivatedTimestamp = deactivatedTimestampStr.isEmpty() ? null : parseDate(deactivatedTimestampStr);

        if (shouldSkipRow(startTimestamp, createTimestamp, deactivatedTimestamp)) {
            return;
        }

        if (deactivatedTimestamp == null) {
            // No deactivated timestamp: treat as active
            ownerToActiveIps.computeIfAbsent(owner, k -> new HashSet<>()).addAll(Arrays.asList(ips));
            contactToActiveIps.computeIfAbsent(contact, k -> new HashSet<>()).addAll(Arrays.asList(ips));
        } else {
            // Has deactivated timestamp: treat as deactivated
            ownerToDeactivatedIps.computeIfAbsent(owner, k -> new HashSet<>()).addAll(Arrays.asList(ips));
            contactToDeactivatedIps.computeIfAbsent(contact, k -> new HashSet<>()).addAll(Arrays.asList(ips));
        }
    }

    /**
     * Determines if a row should be skipped based on the startTimestamp filter.
     * Skips if both create and deactivated timestamps are before the filter.
     *
     * @param startTimestamp The filter timestamp (nullable)
     * @param createTimestamp The create timestamp for the row (nullable)
     * @param deactivatedTimestamp The deactivated timestamp for the row (nullable)
     * @return true if the row should be skipped, false otherwise
     */
    private static boolean shouldSkipRow(LocalDateTime startTimestamp, LocalDateTime createTimestamp, LocalDateTime deactivatedTimestamp) {
        if (startTimestamp == null) {
            return false;
        }
        boolean beforeCreate = (createTimestamp != null && createTimestamp.isBefore(startTimestamp));
        boolean beforeDeactivated = (deactivatedTimestamp != null && deactivatedTimestamp.isBefore(startTimestamp));
        // Skip if both timestamps are before the filter
        if ((createTimestamp != null && beforeCreate) && (deactivatedTimestamp == null || beforeDeactivated)) {
            return true;
        }
        return (deactivatedTimestamp != null && beforeDeactivated) && (createTimestamp == null || beforeCreate);
    }

    /**
     * Parses a date string in the format MM/dd/yyyy hh:mm:ss a to LocalDateTime.
     * @param s the date string
     * @return LocalDateTime object
     */
    public static LocalDateTime parseDate(String s) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a");
        return LocalDateTime.parse(s, fmt);
    }
}