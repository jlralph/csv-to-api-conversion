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
     * Makes an API call to add or remove IPs from a Qualys tag.
     * Exits the application if a fatal error code is returned.
     *
     * @param action        "add" or "remove"
     * @param tagName       Name of the Qualys tag
     * @param ips           Array of IP addresses to add or remove
     * @param errorRecords  List to collect error records
     * @param logger        Logger for output
     */
    public static void makeApiCall(
            String action,
            String tagName,
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

        // Lookup Qualys tag ID by tagName
        String tagId = lookupQualysTagId(tagName, logger);
        if (tagId == null) {
            if ("remove".equals(action)) {
                // If removing and tag doesn't exist, nothing to do
                if (logger.isLoggable(Level.INFO)) {
                    logger.info(String.format("Tag not found for tagName: %s. No removal needed.", tagName));
                }
                return;
            } else {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.warning(String.format("Tag not found for tagName: %s. Attempting to create tag with IPs.", tagName));
                }
                tagId = createQualysTag(tagName, ips, logger);
                if (tagId == null) {
                    String msg = "Failed to create tag for tagName: " + tagName;
                    errorRecords.add("TAG_NOT_FOUND_AND_CREATE_FAILED:" + tagName);
                    logger.severe(msg);
                    return;
                } else {
                    logger.info("Successfully created tag '" + tagName + "' with ID: " + tagId);
                    // No need to call editQualysTag after creation, since IPs were added at creation
                    return;
                }
            }
        }

        // Edit the tag to add or remove IPs
        String editResponse = editQualysTag(tagId, action, ips, logger);

        // Parse the edit response for error codes and add only recognized codes
        if (editResponse != null) {
            String errorCode = QualysApiErrors.extractQualysFoApiErrorCode(editResponse);
            String errorDesc = QualysApiErrors.getDescriptionByCode(errorCode);

            // If the error code is one of the specified, exit the application
            Set<String> fatalCodes = Set.of(
                "1901", "1903", "1904", "1905", "1907", "1908",
                "1920", "1960", "1965", "1922", "1981", "999", "1999",
                "2000", "2002", "2003", "2011", "2012"
            );
            if (errorCode != null && fatalCodes.contains(errorCode)) {
                String msg = String.format(
                    "Fatal Qualys API error code %s (%s) received. Exiting application.",
                    errorCode, errorDesc
                );
                logger.severe(msg);
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
     * Edits the Qualys tag by ID to add or remove IPs using the asset/tag API.
     * Returns the raw API response as a string.
     * Logs request and response details on error.
     *
     * @param tagId The Qualys tag ID
     * @param action "add" or "remove"
     * @param ips Array of IP addresses to add or remove
     * @param logger Logger for output
     * @return The raw API response as a string
     */
    public static String editQualysTag(String tagId, String action, String[] ips, Logger logger) {
        String apiUrl = "https://qualysapi.qualys.com/qps/rest/2.0/update/am/tag/" + tagId;
        String username = "YOUR_QUALYS_USERNAME";
        String password = "YOUR_QUALYS_PASSWORD";

        // Build XML body for the request
        StringBuilder ipList = new StringBuilder();
        for (String ip : ips) {
            ipList.append("<ipAddress>").append(ip).append("</ipAddress>");
        }

        String xmlBody;
        if ("add".equals(action)) {
            xmlBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<ServiceRequest>"
                    + "<data>"
                    + "<addIps>" + ipList + "</addIps>"
                    + "</data>"
                    + "</ServiceRequest>";
        } else {
            xmlBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<ServiceRequest>"
                    + "<data>"
                    + "<removeIps>" + ipList + "</removeIps>"
                    + "</data>"
                    + "</ServiceRequest>";
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            String basicAuth = java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            conn.setRequestProperty("Authorization", "Basic " + basicAuth);
            conn.setRequestProperty("X-Requested-With", "Java");
            conn.setRequestProperty("Content-Type", "application/xml");
            conn.setDoOutput(true);

            // Send request body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(xmlBody.getBytes());
            }

            int responseCode = conn.getResponseCode();
            String response = new String(conn.getInputStream().readAllBytes());
            conn.disconnect();

            if (responseCode != 200) {
                logger.severe(String.format("Failed to update tag %s. HTTP code: %d", tagId, responseCode));
            } else {
                if (logger.isLoggable(Level.INFO)) {
                    logger.info(String.format("Tag %s updated. Response: %s", tagId, response));
                }
            }
            return response;
        } catch (IOException e) {
            // Log the full request and any available response
            logger.severe("IOException during editQualysTag: " + e.getMessage());
            logger.severe("Request URL: " + apiUrl);
            logger.severe("Request Body: " + xmlBody);
            logger.severe("Request Headers: Authorization=Basic ****, X-Requested-With=Java, Content-Type=application/xml");
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
     * Looks up the Qualys tag ID for the given tag name using the asset/tag API.
     * Returns the tag ID as a string, or null if not found.
     * Logs request and response details on error.
     *
     * @param tagName The name of the tag (owner or contact value)
     * @param logger Logger for output
     * @return The Qualys tag ID as a String, or null if not found
     */
    public static String lookupQualysTagId(String tagName, Logger logger) {
        String apiUrl = "https://qualysapi.qualys.com/qps/rest/2.0/search/am/tag";
        String username = "YOUR_QUALYS_USERNAME";
        String password = "YOUR_QUALYS_PASSWORD";

        String xmlBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ServiceRequest>"
                + "<filters>"
                + "<Criteria field=\"name\" operator=\"EQUALS\">" + tagName + "</Criteria>"
                + "</filters>"
                + "</ServiceRequest>";

        HttpURLConnection conn = null;
        try {
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            String basicAuth = java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            conn.setRequestProperty("Authorization", "Basic " + basicAuth);
            conn.setRequestProperty("X-Requested-With", "Java");
            conn.setRequestProperty("Content-Type", "application/xml");
            conn.setDoOutput(true);

            // Send request body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(xmlBody.getBytes());
            }

            int responseCode = conn.getResponseCode();
            String response = new String(conn.getInputStream().readAllBytes());
            conn.disconnect();

            if (responseCode != 200) {
                System.err.println("Failed to look up tag ID for " + tagName + ". HTTP code: " + responseCode);
                return null;
            }

            // Simple extraction (for demo; use proper XML parser in production)
            String idTag = "<id>";
            int idStart = response.indexOf(idTag);
            if (idStart == -1) return null;
            int idEnd = response.indexOf("</id>", idStart);
            if (idEnd == -1) return null;
            return response.substring(idStart + idTag.length(), idEnd).trim();
        } catch (IOException e) {
            // Log the full request and any available response
            logger.severe("IOException during lookupQualysTagId for tag '" + tagName + "': " + e.getMessage());
            logger.severe("Request URL: " + apiUrl);
            logger.severe("Request Body: " + xmlBody);
            logger.severe("Request Headers: Authorization=Basic ****, X-Requested-With=Java, Content-Type=application/xml");
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
     * Creates a new Qualys tag with the given name and (optionally) IPs using the asset/tag API.
     * Returns the new tag ID as a string, or null if creation fails.
     *
     * @param tagName The name of the tag to create
     * @param ips     Array of IP addresses to add to the tag (may be empty)
     * @param logger  Logger for output
     * @return The new Qualys tag ID as a String, or null if creation fails
     */
    public static String createQualysTag(String tagName, String[] ips, Logger logger) {
        String apiUrl = "https://qualysapi.qualys.com/qps/rest/2.0/create/am/tag";
        String username = "YOUR_QUALYS_USERNAME";
        String password = "YOUR_QUALYS_PASSWORD";

        StringBuilder ipList = new StringBuilder();
        if (ips != null && ips.length > 0) {
            for (String ip : ips) {
                ipList.append("<ipAddress>").append(ip).append("</ipAddress>");
            }
        }

        String xmlBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ServiceRequest>"
                + "<data>"
                + "<Tag>"
                + "<name>" + tagName + "</name>";
        if (ipList.length() > 0) {
            xmlBody += "<addIps>" + ipList + "</addIps>";
        }
        xmlBody += "</Tag>"
                + "</data>"
                + "</ServiceRequest>";

        HttpURLConnection conn = null;
        try {
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            String basicAuth = java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            conn.setRequestProperty("Authorization", "Basic " + basicAuth);
            conn.setRequestProperty("X-Requested-With", "Java");
            conn.setRequestProperty("Content-Type", "application/xml");
            conn.setDoOutput(true);

            // Send request body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(xmlBody.getBytes());
            }

            int responseCode = conn.getResponseCode();
            String response = new String(conn.getInputStream().readAllBytes());
            conn.disconnect();

            if (responseCode != 200) {
                logger.severe("Failed to create tag '" + tagName + "'. HTTP code: " + responseCode);
                return null;
            }

            // Simple extraction (for demo; use proper XML parser in production)
            String idTag = "<id>";
            int idStart = response.indexOf(idTag);
            if (idStart == -1) return null;
            int idEnd = response.indexOf("</id>", idStart);
            if (idEnd == -1) return null;
            return response.substring(idStart + idTag.length(), idEnd).trim();
        } catch (IOException e) {
            logger.severe("IOException during createQualysTag for tag '" + tagName + "': " + e.getMessage());
            logger.severe("Request URL: " + apiUrl);
            logger.severe("Request Body: " + xmlBody);
            logger.severe("Request Headers: Authorization=Basic ****, X-Requested-With=Java, Content-Type=application/xml");
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