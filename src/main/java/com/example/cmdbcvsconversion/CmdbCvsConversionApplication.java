package com.example.cmdbcvsconversion;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

import javax.net.ssl.HttpsURLConnection;

public class CmdbCvsConversionApplication {

    /**
     * Entry point for the application.
     * Reads a CSV file, parses each row, and makes an API call for each record.
     * Collects error codes from API responses.
     */
    public static void main(String[] args) throws Exception {
        // Determine CSV file path from arguments or use default sample
        Path csvPath;

        if (args.length < 1) {
            // If no argument is provided, use a default sample CSV path
            csvPath = Paths.get("src/main/resources/sample.csv");
        } else {
            // Use the provided CSV file path
            csvPath = Paths.get(args[0]);
        }

        List<String> errorRecords = new ArrayList<>();

        // Read and process each line of the CSV file
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Parse CSV columns and extract fields
                String[] cols = line.split(",", -1);
                if (cols.length < 6) continue; // skip invalid rows

                String one = cols[0].trim();
                String two = cols[1].trim();
                String three = cols[2].trim();

                // IPs are from index 3 up to (length - 2)
                String[] ips = Arrays.copyOfRange(cols, 3, cols.length - 2);
                for (int i = 0; i < ips.length; i++) ips[i] = ips[i].trim();

                String fiveStr = cols[cols.length - 2].trim();
                String sixStr = cols[cols.length - 1].trim();

                LocalDateTime five = fiveStr.isEmpty() ? null : parseDate(fiveStr);
                LocalDateTime six = sixStr.isEmpty() ? null : parseDate(sixStr);

                // Make API call and collect error codes if present
                makeApiCall(one, two, three, ips, five, six, errorRecords);
            }
        }

        // Output all error codes found in API responses
        System.out.println("Error Records: " + errorRecords);
    }

    /**
     * Parses a date string in the format MM/dd/yyyy hh:mm:ss a to LocalDateTime.
     * @param s the date string
     * @return LocalDateTime object
     */
    private static LocalDateTime parseDate(String s) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a");
        return LocalDateTime.parse(s, fmt);
    }

    /**
     * Makes an HTTPS POST API call with the given data as JSON.
     * Reads the JSON response, extracts the "code" field, and adds it to errorRecords if it matches known Qualys error codes.
     *
     * @param one, two, three, ips, five, six - data fields for the API call
     * @param errorRecords - list to collect found error codes
     */
    private static void makeApiCall(
        String one, String two, String three, String[] ips,
        LocalDateTime five, LocalDateTime six,
        List<String> errorRecords
    ) throws IOException {
        // Prepare the API endpoint and open connection
        URI uri = URI.create("https://example.com/api");
        URL url = uri.toURL();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        // Build JSON payload for API request
        String json = buildJsonPayload(one, two, three, ips, five, six);
        System.out.println(json);

        // Send JSON payload to API
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes());
        }

        // Get HTTP response code and message for logging
        int responseCode = conn.getResponseCode();
        System.out.println("API Response Code: " + responseCode);
        System.out.println("API Response Message: " + conn.getResponseMessage());

        // Read API response body as a string
        String response = readApiResponse(conn, responseCode);
        conn.disconnect();

        // Extract "code" field from JSON response
        String code = extractCodeFromResponse(response);

        // If code matches a known error or is not "SUCCESS", add to errorRecords
        if (shouldAddErrorRecord(code)) {
            errorRecords.add(code);
        }
    }

    /**
     * Builds the JSON payload for the API request.
     * @param one, two, three, ips, five, six - data fields
     * @return JSON string
     */
    private static String buildJsonPayload(String one, String two, String three, String[] ips, LocalDateTime five, LocalDateTime six) {
        StringBuilder ipsJson = new StringBuilder("[");
        for (int i = 0; i < ips.length; i++) {
            ipsJson.append("\"").append(ips[i]).append("\"");
            if (i < ips.length - 1) ipsJson.append(",");
        }
        ipsJson.append("]");

        return """
    {
        "one": "%s",
        "two": "%s",
        "three": "%s",
        "ips": %s,
        "five": %s,
        "six": %s
    }
    """.formatted(
                one, two, three,
                ipsJson.toString(),
                five == null ? "null" : "\"" + five.toString() + "\"",
                six == null ? "null" : "\"" + six.toString() + "\""
        );
    }

    /**
     * Reads the API response body as a string.
     * Uses error stream for HTTP error codes, input stream otherwise.
     * @param conn the HttpsURLConnection
     * @param responseCode the HTTP response code
     * @return the response body as a string
     */
    private static String readApiResponse(HttpsURLConnection conn, int responseCode) throws IOException {
        StringBuilder responseBody = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                responseBody.append(inputLine);
            }
        }
        return responseBody.toString();
    }

    /**
     * Extracts the "code" field from a JSON response string.
     * This is a simple string search, not a full JSON parser.
     * @param response the JSON response string
     * @return the code as a string, or null if not found
     */
    private static String extractCodeFromResponse(String response) {
        String code = null;
        int codeIdx = response.indexOf("\"code\"");
        if (codeIdx != -1) {
            int colonIdx = response.indexOf(":", codeIdx);
            if (colonIdx != -1) {
                int start = colonIdx + 1;
                while (start < response.length() && !Character.isDigit(response.charAt(start))) start++;
                int end = start;
                while (end < response.length() && Character.isDigit(response.charAt(end))) end++;
                if (start < end) {
                    code = response.substring(start, end);
                }
            }
        }
        return code;
    }

    /**
     * Determines if an error code should be added to errorRecords.
     * Returns true if the code is a known Qualys error or is not "SUCCESS".
     * @param code the error code string
     * @return true if should add, false otherwise
     */
    private static boolean shouldAddErrorRecord(String code) {
        if (code == null) return false;
        String desc = QualysApiErrors.getDescriptionByCode(code);
        return (desc != null && !"Unknown error code".equals(desc)) || !"SUCCESS".equals(code);
    }
}

/**
 * Utility class for Qualys API error code lookups.
 * Provides mappings between error codes and their descriptions.
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