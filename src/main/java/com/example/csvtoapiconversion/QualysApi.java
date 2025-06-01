package com.example.csvtoapiconversion;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

/**
 * Utility class for making Qualys API calls to add or remove IPs from asset groups.
 * Handles error code parsing and fatal error handling.
 */
public class QualysApi {

    /**
     * Makes an API call to add or remove IPs from a Qualys asset group.
     * Exits the application if a fatal error code is returned.
     *
     * @param action        "add" or "remove"
     * @param groupName     Name of the Qualys asset group
     * @param ips           Array of IP addresses to add or remove
     * @param errorRecords  List to collect error records
     * @param logger        Logger for output
     */
    public static void makeApiCall(
            String action,
            String groupName,
            String[] ips,
            List<String> errorRecords,
            Logger logger
    ) {
        // Validate action
        if (!"add".equals(action) && !"remove".equals(action)) {
            String msg = "action must be 'add' or 'remove'";
            logger.severe(msg);
            throw new IllegalArgumentException(msg);
        }

        // Lookup Qualys asset group ID by groupName
        String groupId = lookupQualysGroupId(groupName, logger);
        if (groupId == null) {
            String msg = "Asset group not found for groupName: " + groupName;
            errorRecords.add("GROUP_NOT_FOUND:" + groupName);
            logger.warning(msg);
            return;
        }

        // Edit the asset group to add or remove IPs
        String editResponse = editQualysAssetGroup(groupId, action, ips, logger);

        // Parse the edit response for error codes and add only recognized codes
        if (editResponse != null) {
            String errorCode = QualysApiErrors.extractQualysFoApiErrorCode(editResponse);
            String errorDesc = QualysApiErrors.getDescriptionByCode(errorCode);

            // If the error code is one of the specified, exit the application
            Set<String> fatalCodes = Set.of(
                "1920", "1960", "1965", "1981",
                "999", "1999", "2000", "2002", "2003", "2011", "2012"
            );
            if (errorCode != null && fatalCodes.contains(errorCode)) {
                String msg = String.format(
                    "Fatal Qualys API error code %s (%s) received. Exiting application.",
                    errorCode, errorDesc
                );
                logger.severe(msg);
                System.err.println(msg);
                System.exit(1);
            }

            if (errorCode != null && !"Unknown error code".equals(errorDesc)) {
                String msg = errorCode + ": " + errorDesc;
                errorRecords.add(msg);
                logger.warning(msg);
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
     * @param logger Logger for output
     * @return The raw API response as a string
     */
    private static String editQualysAssetGroup(String groupId, String action, String[] ips, Logger logger) {
        String apiUrl = "https://qualysapi.qualys.com/api/2.0/fo/asset/group/";
        String username = "YOUR_QUALYS_USERNAME";
        String password = "YOUR_QUALYS_PASSWORD";

        // Build comma-separated IP list
        StringBuilder ipList = new StringBuilder();
        for (int i = 0; i < ips.length; i++) {
            ipList.append(ips[i]);
            if (i < ips.length - 1) ipList.append(",");
        }

        // Build request parameters
        String params;
        if ("add".equals(action)) {
            params = "action=edit&id=" + URLEncoder.encode(groupId, java.nio.charset.StandardCharsets.UTF_8) +
                    "&add_ips=" + URLEncoder.encode(ipList.toString(), java.nio.charset.StandardCharsets.UTF_8);
        } else {
            params = "action=edit&id=" + URLEncoder.encode(groupId, java.nio.charset.StandardCharsets.UTF_8) +
                    "&remove_ips=" + URLEncoder.encode(ipList.toString(), java.nio.charset.StandardCharsets.UTF_8);
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

            // Send request body
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
            logger.severe("IOException during editQualysAssetGroup: " + e.getMessage());
            logger.severe("Request URL: " + apiUrl);
            logger.severe("Request Params: " + params);
            logger.severe("Request Headers: Authorization=Basic ****, X-Requested-With=Java");
            if (conn != null) {
                try {
                    int code = conn.getResponseCode();
                    String response = "";
                    try (InputStream is = conn.getInputStream()) {
                        response = new String(is.readAllBytes());
                    } catch (IOException ex) {
                        // Ignore, may not be available
                    }
                    logger.severe("HTTP Response Code: " + code);
                    logger.severe("HTTP Response Body:\n" + response);
                } catch (IOException ex) {
                    logger.severe("Unable to read response: " + ex.getMessage());
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
     * @param logger Logger for output
     * @return The Qualys asset group ID as a String, or null if not found
     */
    private static String lookupQualysGroupId(String groupName, Logger logger) {
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
            logger.severe("IOException during lookupQualysGroupId for group '" + groupName + "': " + e.getMessage());
            logger.severe("Request URL: " + apiUrl + "?" + params);
            logger.severe("Request Headers: Authorization=Basic ****, X-Requested-With=Java");
            if (conn != null) {
                try {
                    int code = conn.getResponseCode();
                    String response = "";
                    try (InputStream is = conn.getInputStream()) {
                        response = new String(is.readAllBytes());
                    } catch (IOException ex) {
                        // Ignore, may not be available
                    }
                    logger.severe("HTTP Response Code: " + code);
                    logger.severe("HTTP Response Body:\n" + response);
                } catch (IOException ex) {
                    logger.severe("Unable to read response: " + ex.getMessage());
                }
            }
            return null;
        }
    }
}