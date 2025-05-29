package com.example.csvtoapiconversion;

import java.util.*;

/**
 * Utility class for Qualys API error code lookups.
 * Provides mappings between error codes and their descriptions.
 * Used to filter and describe error codes returned by the Qualys API.
 */
public class QualysApiErrors {
    // Maps error code to description
    private static final Map<String, String> ERROR_CODE_TO_DESCRIPTION;
    // Maps description (lowercase) to error code
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

        // Reverse map for description to error code (case-insensitive)
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
     * Extracts the Qualys FO API error code from the API response string.
     * This method looks for <CODE>...</CODE> in the XML response.
     * Returns the code as a string, or null if not found.
     */
    public static String extractQualysFoApiErrorCode(String response) {
        if (response == null) return null;
        String codeTag = "<CODE>";
        int codeStart = response.indexOf(codeTag);
        if (codeStart == -1) return null;
        int codeEnd = response.indexOf("</CODE>", codeStart);
        if (codeEnd == -1) return null;
        return response.substring(codeStart + codeTag.length(), codeEnd).trim();
    }

    /**
     * Main method demonstrating the usage of QualysApiErrors class.
     */
    public static void main(String[] args) {
        String code = "1901";
        String description = getDescriptionByCode(code);
        System.out.println("Error code " + code + ": " + description);

        String descQuery = "Bad Login/Password";
        String foundCode = getCodeByDescription(descQuery);
        System.out.println("Description \"" + descQuery + "\": Error code " + foundCode);

        String unknownCode = "9999";
        System.out.println("Error code " + unknownCode + ": " + getDescriptionByCode(unknownCode));

        String unknownDesc = "Nonexistent Error";
        System.out.println("Description \"" + unknownDesc + "\": Error code " + getCodeByDescription(unknownDesc));
    }
}