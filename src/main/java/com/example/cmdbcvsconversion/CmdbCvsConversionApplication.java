package com.example.cmdbcvsconversion;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

import javax.net.ssl.HttpsURLConnection;

public class CmdbCvsConversionApplication {
    public static void main(String[] args) throws Exception {
        Path csvPath;

        if (args.length < 1) {
          //  System.out.println("Usage: java -jar cmdb-cvs-conversion.jar <input.csv>");
            csvPath = Paths.get("src/main/resources/sample.csv");
        }else {
            csvPath = Paths.get(args[0]);
        }
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
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

                // Example: Make an HTTPS POST call for each row
                makeApiCall(one, two, three, ips, five, six);
            }
        }
    }

    private static LocalDateTime parseDate(String s) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a");
        return LocalDateTime.parse(s, fmt);
    }

    private static void makeApiCall(String one, String two, String three, String[] ips, LocalDateTime five, LocalDateTime six) throws IOException {
//        URI uri = URI.create("https://example.com/api");
//        URL url = uri.toURL();
 //       HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
 //       conn.setRequestMethod("POST");
//        conn.setDoOutput(true);
//        conn.setRequestProperty("Content-Type", "application/json");

        StringBuilder ipsJson = new StringBuilder("[");
        for (int i = 0; i < ips.length; i++) {
            ipsJson.append("\"").append(ips[i]).append("\"");
            if (i < ips.length - 1) ipsJson.append(",");
        }
        ipsJson.append("]");

        String json = """
    {
        "one": "%s",
        "two": "%s",
        "three": "%s",
        "ips": %s,
        "five": "%s",
        "six": "%s"
    }
    """.formatted(
        one, two, three,
        ipsJson.toString(),
        five == null ? "null" : "\"" + five.toString() + "\"",
        six == null ? "null" : "\"" + six.toString() + "\""
    );

            System.out.println(json);

  //      try (OutputStream os = conn.getOutputStream()) {
   //         os.write(json.getBytes());
    //    }

      //  int responseCode = conn.getResponseCode();
        
        //System.out.println("API Response Code: " + responseCode);
        //System.out.println("API Response Message: " + conn.getResponseMessage());
        //conn.disconnect();
    }
}