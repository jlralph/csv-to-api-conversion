package com.example.cmdbcvsconversion;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

public class QualysApi {

    public static void makeApiCall(
            String action,
            String groupName,
            String[] ips,
            List<String> errorRecords,
            Logger logger
    ) {
        if (!"add".equals(action) && !"remove".equals(action)) {
            String msg = "action must be 'add' or 'remove'";
            logger.severe(msg);
            throw new IllegalArgumentException(msg);
        }

        String groupId = lookupQualysGroupId(groupName, logger);
        if (groupId == null) {
            String msg = "Asset group not found for groupName: " + groupName;
            errorRecords.add("GROUP_NOT_FOUND:" + groupName);
            logger.warning(msg);
            System.err.println(msg);
            return;
        }

        String editResponse = editQualysAssetGroup(groupId, action, ips, logger);

        if (editResponse != null) {
            String errorCode = QualysApiErrors.extractQualysFoApiErrorCode(editResponse);
            String errorDesc = QualysApiErrors.getDescriptionByCode(errorCode);
            if (errorCode != null && !"Unknown error code".equals(errorDesc)) {
                String msg = errorCode + ": " + errorDesc;
                errorRecords.add(msg);
                logger.warning(msg);
            }
        }
    }

    private static String editQualysAssetGroup(String groupId, String action, String[] ips, Logger logger) {
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

            String idTag = "<ID>";
            int idStart = response.indexOf(idTag);
            if (idStart == -1) return null;
            int idEnd = response.indexOf("</ID>", idStart);
            if (idEnd == -1) return null;
            return response.substring(idStart + idTag.length(), idEnd).trim();
        } catch (IOException e) {
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