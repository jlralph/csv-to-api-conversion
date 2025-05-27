package com.example.cmdbcvsconversion;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.logging.*;

import javax.net.ssl.HttpsURLConnection;

public class CmdbCvsConversionApplication {

    // Logger setup for both file and console output
    private static final Logger LOGGER = Logger.getLogger(CmdbCvsConversionApplication.class.getName());
    static {
        try {
            // Log to file "cmdb-cvs-conversion.log" in append mode
            FileHandler fileHandler = new FileHandler("cmdb-cvs-conversion.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setUseParentHandlers(true); // Also log to console
        } catch (IOException e) {
            System.err.println("Failed to set up file logger: " + e.getMessage());
        }
    }

    /**
     * Entry point for the application.
     * Reads a CSV file, parses each row, and builds four maps:
     *   - ownerToActiveIps: owner → set of active IPs (no deactivated timestamp)
     *   - contactToActiveIps: contact → set of active IPs (no deactivated timestamp)
     *   - ownerToDeactivatedIps: owner → set of deactivated IPs (has deactivated timestamp)
     *   - contactToDeactivatedIps: contact → set of deactivated IPs (has deactivated timestamp)
     *
     * If a start timestamp is provided as the second argument, only includes records where
     * either the create or deactivated timestamp is after or equal to the start timestamp.
     *
     * For each map, makes Qualys API calls to add or remove IPs from asset groups.
     * Removals (deactivated IPs) are processed before additions (active IPs).
     * If the third argument is true, API calls are suppressed and only dry-run output is printed.
     * Collects error codes and descriptions from API responses.
     * Logs all summary output to both the logger and the console.
     */
    public static void main(String[] args) throws Exception {
        // Determine CSV file path from arguments or use default sample
        Path csvPath;
        LocalDateTime startTimestamp = null;
        boolean suppressApiCall = false; // If true, do not make API calls (dry run)

        if (args.length < 1) {
            // If no argument is provided, use a default sample CSV path
            csvPath = Paths.get("src/main/resources/sample.csv");
        } else {
            // Use the provided CSV file path
            csvPath = Paths.get(args[0]);
        }

        // Optional: parse start timestamp from args[1] if present
        if (args.length >= 2) {
            try {
                startTimestamp = parseDate(args[1]);
            } catch (Exception e) {
                System.err.println("Invalid start timestamp format. Expected: MM/dd/yyyy hh:mm:ss a");
                startTimestamp = null;
            }
        }

        // Optional: suppress API call flag as third argument (dry run)
        if (args.length >= 3 && args[2] != null && !args[2].isBlank()) {
            suppressApiCall = Boolean.parseBoolean(args[2]);
        } else {
            suppressApiCall = false; // Default to false if not specified
        }

        List<String> errorRecords = new ArrayList<>();

        // Maps for owner/contact to active/deactivated IPs
        Map<String, Set<String>> ownerToActiveIps = new HashMap<>();
        Map<String, Set<String>> contactToActiveIps = new HashMap<>();
        Map<String, Set<String>> ownerToDeactivatedIps = new HashMap<>();
        Map<String, Set<String>> contactToDeactivatedIps = new HashMap<>();

        // Read and process each line of the CSV file
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Parse CSV columns and extract fields
                String[] cols = line.split(",", -1);
                if (cols.length < 6) continue; // skip invalid rows

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

                // Filter: skip if startTimestamp is set and both create and deactivated are before it
                if (startTimestamp != null) {
                    boolean beforeCreate = (createTimestamp != null && createTimestamp.isBefore(startTimestamp));
                    boolean beforeDeactivated = (deactivatedTimestamp != null && deactivatedTimestamp.isBefore(startTimestamp));
                    // If both are present and both are before, skip
                    // If only one is present and it's before, skip
                    if ((createTimestamp != null && beforeCreate && (deactivatedTimestamp == null || beforeDeactivated))
                        || (deactivatedTimestamp != null && beforeDeactivated && (createTimestamp == null || beforeCreate))) {
                        continue;
                    }
                }

                if (deactivatedTimestamp == null) {
                    // No deactivatedTimestamp: add IPs to owner's and contact's set
                    ownerToActiveIps.computeIfAbsent(owner, k -> new HashSet<>()).addAll(Arrays.asList(ips));
                    contactToActiveIps.computeIfAbsent(contact, k -> new HashSet<>()).addAll(Arrays.asList(ips));
                } else {
                    // Has deactivatedTimestamp: add IPs to deactivated sets by owner and contact
                    ownerToDeactivatedIps.computeIfAbsent(owner, k -> new HashSet<>()).addAll(Arrays.asList(ips));
                    contactToDeactivatedIps.computeIfAbsent(contact, k -> new HashSet<>()).addAll(Arrays.asList(ips));
                }
            }
        }

        // Make API calls for each entry in the four maps
        // Removals (deactivated IPs) are processed before additions (active IPs)
        for (Map.Entry<String, Set<String>> entry : ownerToDeactivatedIps.entrySet()) {
            String owner = entry.getKey();
            Set<String> ips = entry.getValue();
            if (!suppressApiCall) {
                makeApiCall("remove", owner, ips.toArray(new String[0]), null, LocalDateTime.now(), errorRecords);
            } else {
                System.out.println("[DRY RUN] Would remove IPs " + ips + " from owner group: " + owner);
            }
        }

        for (Map.Entry<String, Set<String>> entry : contactToDeactivatedIps.entrySet()) {
            String contact = entry.getKey();
            Set<String> ips = entry.getValue();
            if (!suppressApiCall) {
                makeApiCall("remove", contact, ips.toArray(new String[0]), null, LocalDateTime.now(), errorRecords);
            } else {
                System.out.println("[DRY RUN] Would remove IPs " + ips + " from contact group: " + contact);
            }
        }

        // Then, process additions (active IPs)
        for (Map.Entry<String, Set<String>> entry : ownerToActiveIps.entrySet()) {
            String owner = entry.getKey();
            Set<String> ips = entry.getValue();
            if (!suppressApiCall) {
                makeApiCall("add", owner, ips.toArray(new String[0]), null, null, errorRecords);
            } else {
                System.out.println("[DRY RUN] Would add IPs " + ips + " to owner group: " + owner);
            }
        }

        for (Map.Entry<String, Set<String>> entry : contactToActiveIps.entrySet()) {
            String contact = entry.getKey();
            Set<String> ips = entry.getValue();
            if (!suppressApiCall) {
                makeApiCall("add", contact, ips.toArray(new String[0]), null, null, errorRecords);
            } else {
                System.out.println("[DRY RUN] Would add IPs " + ips + " to contact group: " + contact);
            }
        }

        // Output all error codes found in API responses
        System.out.println("Error Records: " + errorRecords);

        // Output owner to active IPs map
        LOGGER.info("Owner to Active IPs:");
        for (Map.Entry<String, Set<String>> entry : ownerToActiveIps.entrySet()) {
            String msg = "Owner: " + entry.getKey() + " -> IPs: " + entry.getValue();
            LOGGER.info(msg);
        }

        // Output contact to active IPs map
        LOGGER.info("Contact to Active IPs:");
        for (Map.Entry<String, Set<String>> entry : contactToActiveIps.entrySet()) {
            String msg = "Contact: " + entry.getKey() + " -> IPs: " + entry.getValue();
            LOGGER.info(msg);
        }

        // Output owner to deactivated IPs map
        LOGGER.info("Owner to Deactivated IPs:");
        for (Map.Entry<String, Set<String>> entry : ownerToDeactivatedIps.entrySet()) {
            String msg = "Owner: " + entry.getKey() + " -> Deactivated IPs: " + entry.getValue();
            LOGGER.info(msg);
        }

        // Output contact to deactivated IPs map
        LOGGER.info("Contact to Deactivated IPs:");
        for (Map.Entry<String, Set<String>> entry : contactToDeactivatedIps.entrySet()) {
            String msg = "Contact: " + entry.getKey() + " -> Deactivated IPs: " + entry.getValue();
            LOGGER.info(msg);
        }

        String errorMsg = "Error Records: " + errorRecords;
        LOGGER.info(errorMsg);
    }

    /**
     * Parses a date string in the format MM/dd/yyyy hh:mm:ss a to LocalDateTime.
     * @param s the date string
     * @return LocalDateTime object
     */
    static LocalDateTime parseDate(String s) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a");
        return LocalDateTime.parse(s, fmt);
    }

    /**
     * Makes an API call to Qualys to look up the asset group ID by groupName,
     * then edits the asset group to add or remove the given IPs.
     * Parses both API responses for error codes and adds them to errorRecords,
     * but only if the error code is recognized in QualysApiErrors.
     * Logs request and response details on error.
     *
     * @param action ("add" or "remove")
     * @param groupName (owner or contact value)
     * @param ips IP addresses to add or remove
     * @param createTimestamp Not used in API call, included for completeness
     * @param deactivatedTimestamp Not used in API call, included for completeness
     * @param errorRecords List to collect found error codes and descriptions
     */
    static void makeApiCall(
        String action, String groupName, String[] ips,
        LocalDateTime createTimestamp, LocalDateTime deactivatedTimestamp,
        List<String> errorRecords
    ) {
        // Validate action
        if (!"add".equals(action) && !"remove".equals(action)) {
            String msg = "action must be 'add' or 'remove'";
            LOGGER.severe(msg);
            throw new IllegalArgumentException(msg);
        }

        // Lookup Qualys asset group ID by groupName
        String groupId = lookupQualysGroupId(groupName);
        if (groupId == null) {
            String msg = "Asset group not found for groupName: " + groupName;
            errorRecords.add("GROUP_NOT_FOUND:" + groupName);
            LOGGER.warning(msg);
            System.err.println(msg);
            return;
        }

        // Edit the asset group to add or remove IPs
        String editResponse = editQualysAssetGroup(groupId, action, ips);

        // Parse the edit response for error codes and add only recognized codes
        if (editResponse != null) {
            String errorCode = extractQualysFoApiErrorCode(editResponse);
            String errorDesc = QualysApiErrors.getDescriptionByCode(errorCode);
            if (errorCode != null && !"Unknown error code".equals(errorDesc)) {
                String msg = errorCode + ": " + errorDesc;
                errorRecords.add(msg);
                LOGGER.warning(msg);
            }
        }
    }

    /**
     * Edits the Qualys asset group by ID to add or remove IPs using the fo/asset/group API.
     * Returns the raw API response as a string.
     * Logs request and response details on error.
     *
     * @param groupId The Qualys asset group ID
     * @param action "add" or "remove"
     * @param ips Array of IP addresses to add or remove
     * @return The raw API response as a string
     */
    private static String editQualysAssetGroup(String groupId, String action, String[] ips) {
        String apiUrl = "https://qualysapi.qualys.com/api/2.0/fo/asset/group/";
        String username = "YOUR_QUALYS_USERNAME";
        String password = "YOUR_QUALYS_PASSWORD";

        StringBuilder ipList = new StringBuilder();
        for (int i = 0; i < ips.length; i++) {
            ipList.append(ips[i]);
            if (i < ips.length - 1) ipList.append(",");
        }

        String params;
        if ("add".equals(action)) {
            params = "action=edit&id=" + URLEncoder.encode(groupId, java.nio.charset.StandardCharsets.UTF_8) +
                     "&add_ips=" + URLEncoder.encode(ipList.toString(), java.nio.charset.StandardCharsets.UTF_8);
        } else if ("remove".equals(action)) {
            params = "action=edit&id=" + URLEncoder.encode(groupId, java.nio.charset.StandardCharsets.UTF_8) +
                     "&remove_ips=" + URLEncoder.encode(ipList.toString(), java.nio.charset.StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("action must be 'add' or 'remove'");
        }

        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(apiUrl);
            URL url = uri.toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            String basicAuth = java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            conn.setRequestProperty("Authorization", "Basic " + basicAuth);
            conn.setRequestProperty("X-Requested-With", "Java");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes());
            }

            int responseCode = conn.getResponseCode();
            String response = new String(conn.getInputStream().readAllBytes());
            conn.disconnect();

            if (responseCode != 200) {
                System.err.println("Failed to update asset group " + groupId + ". HTTP code: " + responseCode);
            } else {
                System.out.println("Asset group " + groupId + " updated. Response: " + response);
            }
            return response;
        } catch (IOException e) {
            // Log the full request and any available response
            LOGGER.severe("IOException during editQualysAssetGroup: " + e.getMessage());
            LOGGER.severe("Request URL: " + apiUrl);
            LOGGER.severe("Request Params: " + params);
            LOGGER.severe("Request Headers: Authorization=Basic ****, X-Requested-With=Java");
            if (conn != null) {
                try {
                    int code = conn.getResponseCode();
                    String response = "";
                    try (InputStream is = conn.getInputStream()) {
                        response = new String(is.readAllBytes());
                    } catch (IOException ex) {
                        // Ignore, may not be available
                    }
                    LOGGER.severe("HTTP Response Code: " + code);
                    LOGGER.severe("HTTP Response Body:\n" + response);
                } catch (IOException ex) {
                    LOGGER.severe("Unable to read response: " + ex.getMessage());
                }
            }
            return null;
        }
    }

    /**
     * Looks up the Qualys asset group ID for the given group name using the fo/asset/group API.
     * Returns the group ID as a string, or null if not found.
     * Logs request and response details on error.
     *
     * @param groupName The name of the asset group (owner or contact value)
     * @return The Qualys asset group ID as a String, or null if not found
     */
    private static String lookupQualysGroupId(String groupName) {
        String apiUrl = "https://qualysapi.qualys.com/api/2.0/fo/asset/group/";
        String username = "YOUR_QUALYS_USERNAME";
        String password = "YOUR_QUALYS_PASSWORD";
        String params = "action=list&title=" + URLEncoder.encode(groupName, java.nio.charset.StandardCharsets.UTF_8);

        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(apiUrl + "?" + params);
            URL url = uri.toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            String basicAuth = java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            conn.setRequestProperty("Authorization", "Basic " + basicAuth);
            conn.setRequestProperty("X-Requested-With", "Java");

            int responseCode = conn.getResponseCode();
            String response = new String(conn.getInputStream().readAllBytes());
            conn.disconnect();

            System.err.println("Response body:\n" + response);
            if (responseCode != 200) {
                System.err.println("Failed to look up group ID for " + groupName + ". HTTP code: " + responseCode);
                return null;
            }

            // Simple extraction (for demo; use proper XML parser in production)
            String idTag = "<ID>";
            int idStart = response.indexOf(idTag);
            if (idStart == -1) return null;
            int idEnd = response.indexOf("</ID>", idStart);
            if (idEnd == -1) return null;
            return response.substring(idStart + idTag.length(), idEnd).trim();
        } catch (IOException e) {
            // Log the full request and any available response
            LOGGER.severe("IOException during lookupQualysGroupId for group '" + groupName + "': " + e.getMessage());
            LOGGER.severe("Request URL: " + apiUrl + "?" + params);
            LOGGER.severe("Request Headers: Authorization=Basic ****, X-Requested-With=Java");
            if (conn != null) {
                try {
                    int code = conn.getResponseCode();
                    String response = "";
                    try (InputStream is = conn.getInputStream()) {
                        response = new String(is.readAllBytes());
                    } catch (IOException ex) {
                        // Ignore, may not be available
                    }
                    LOGGER.severe("HTTP Response Code: " + code);
                    LOGGER.severe("HTTP Response Body:\n" + response);
                } catch (IOException ex) {
                    LOGGER.severe("Unable to read response: " + ex.getMessage());
                }
            }
            return null;
        }
    }

    /**
     * Extracts the Qualys FO API error code from the API response string.
     * This method looks for <CODE>...</CODE> in the XML response.
     * Returns the code as a string, or null if not found.
     */
    static String extractQualysFoApiErrorCode(String response) {
        if (response == null) return null;
        String codeTag = "<CODE>";
        int codeStart = response.indexOf(codeTag);
        if (codeStart == -1) return null;
        int codeEnd = response.indexOf("</CODE>", codeStart);
        if (codeEnd == -1) return null;
        return response.substring(codeStart + codeTag.length(), codeEnd).trim();
    }
}

/**
 * Utility class for Qualys API error code lookups.
 * Provides mappings between error codes and their descriptions.
 * Used to filter and describe error codes returned by the Qualys API.
 */
class QualysApiErrors {
    // Immutable map of error codes to their descriptions
    private static final Map<String, String> ERROR_CODE_TO_DESCRIPTION;
    private static final Map<String, String> DESCRIPTION_TO_ERROR_CODE;

    static {
        Map<String, String> codeToDesc = new HashMap<>();
        // Populate error code to description map
        codeToDesc.put("1901", "Unrecognized parameter(s)");
        codeToDesc.put("1903", "Missing required parameter(s)");
        codeToDesc.put("1904", "Please specify only one of these parameters");
        codeToDesc.put("1905", "Parameter has invalid value");
        codeToDesc.put("1907", "The following combination of key=value pairs is not supported");
        codeToDesc.put("1908", "Request method (GET or POST) is incompatible with specified parameter(s)");
        codeToDesc.put("1920", "The requested operation is blocked by one or more existing Business Objects (generic conflict)");
        codeToDesc.put("1960", "The requested operation is blocked by one or more existing Business Objects (concurrency limit)");
        codeToDesc.put("1965", "The requested operation is blocked by one or more existing Business Objects (rate limit)");
        codeToDesc.put("1922", "Please specify at least one of the following parameters");
        codeToDesc.put("1981", "Your request is being processed. Please try this same request again later");
        codeToDesc.put("999", "Internal Error");
        codeToDesc.put("1999", "We are performing scheduled maintenance on our System. We apologize for any inconvenience");
        codeToDesc.put("2000", "Bad Login/Password");
        codeToDesc.put("2002", "User account is inactive");
        codeToDesc.put("2003", "Registration must be completed before API requests will be served for this account");
        codeToDesc.put("2011", "SecureID authentication is required for this account, so API access is blocked");
        codeToDesc.put("2012", "User license is not authorized to run this API");

        ERROR_CODE_TO_DESCRIPTION = Collections.unmodifiableMap(codeToDesc);

        // Reverse map for description to error code
        Map<String, String> descToCode = new HashMap<>();
        for (Map.Entry<String, String> entry : codeToDesc.entrySet()) {
            descToCode.put(entry.getValue().toLowerCase(), entry.getKey());
        }
        DESCRIPTION_TO_ERROR_CODE = Collections.unmodifiableMap(descToCode);
    }

    /**
     * Get the description for a given error code.
     *
     * @param code Qualys API error code as string
     * @return Description if found, otherwise "Unknown error code"
     */
    public static String getDescriptionByCode(String code) {
        return ERROR_CODE_TO_DESCRIPTION.getOrDefault(code, "Unknown error code");
    }

    /**
     * Get the error code for a given description.
     *
     * @param description Qualys API error description as string (case-insensitive)
     * @return Error code if found, otherwise "Unknown description"
     */
    public static String getCodeByDescription(String description) {
        return DESCRIPTION_TO_ERROR_CODE.getOrDefault(description.toLowerCase(), "Unknown description");
    }

    /**
     * Main method demonstrating the usage of QualysApiErrors class.
     */
    public static void main(String[] args) {
        // Example: Lookup description by error code
        String code = "1901";
        String description = getDescriptionByCode(code);
        System.out.println("Error code " + code + ": " + description);

        // Example: Lookup error code by description (case-insensitive)
        String descQuery = "Bad Login/Password";
        String foundCode = getCodeByDescription(descQuery);
        System.out.println("Description \"" + descQuery + "\": Error code " + foundCode);

        // Example: Lookup with an unknown error code
        String unknownCode = "9999";
        System.out.println("Error code " + unknownCode + ": " + getDescriptionByCode(unknownCode));

        // Example: Lookup with an unknown description
        String unknownDesc = "Nonexistent Error";
        System.out.println("Description \"" + unknownDesc + "\": Error code " + getCodeByDescription(unknownDesc));
    }
}