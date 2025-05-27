package com.example.cmdbcvsconversion;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

public class CsvUtils {

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
                String[] cols = line.split(",", -1);
                if (cols.length < 6) continue;

                String assetName = cols[0].trim();
                String contact = cols[1].trim();
                String owner = cols[2].trim();

                String[] ips = Arrays.copyOfRange(cols, 3, cols.length - 2);
                for (int i = 0; i < ips.length; i++) ips[i] = ips[i].trim();

                String createTimestampStr = cols[cols.length - 2].trim();
                String deactivatedTimestampStr = cols[cols.length - 1].trim();

                LocalDateTime createTimestamp = createTimestampStr.isEmpty() ? null : parseDate(createTimestampStr);
                LocalDateTime deactivatedTimestamp = deactivatedTimestampStr.isEmpty() ? null : parseDate(deactivatedTimestampStr);

                if (startTimestamp != null) {
                    boolean beforeCreate = (createTimestamp != null && createTimestamp.isBefore(startTimestamp));
                    boolean beforeDeactivated = (deactivatedTimestamp != null && deactivatedTimestamp.isBefore(startTimestamp));
                    if ((createTimestamp != null && beforeCreate && (deactivatedTimestamp == null || beforeDeactivated))
                        || (deactivatedTimestamp != null && beforeDeactivated && (createTimestamp == null || beforeCreate))) {
                        continue;
                    }
                }

                if (deactivatedTimestamp == null) {
                    ownerToActiveIps.computeIfAbsent(owner, k -> new HashSet<>()).addAll(Arrays.asList(ips));
                    contactToActiveIps.computeIfAbsent(contact, k -> new HashSet<>()).addAll(Arrays.asList(ips));
                } else {
                    ownerToDeactivatedIps.computeIfAbsent(owner, k -> new HashSet<>()).addAll(Arrays.asList(ips));
                    contactToDeactivatedIps.computeIfAbsent(contact, k -> new HashSet<>()).addAll(Arrays.asList(ips));
                }
            }
        }
    }

    public static LocalDateTime parseDate(String s) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a");
        return LocalDateTime.parse(s, fmt);
    }
}