# csv-to-api-conversion

A Java 21 application that reads a CSV file, parses each row, and (optionally) makes Qualys API calls to manage asset **tags**. The app builds summary maps of owners and contacts to their associated IP addresses, separated by active and deactivated status. All summary output and errors are logged to both the console and a file.

## Project Setup

- **Build Tool:** Maven
- **Java Version:** 21
- **Dependencies:** JUnit 5, Mockito (for testing), Byte Buddy (transitive, for mocking static methods)

## CSV Input Format

Each row in the CSV should have the following columns:

1. **Asset Name** (String)
2. **Contact** (String, e.g., support group name)
3. **Owner** (String, e.g., IT team name)
4. **IP Addresses** (one or more, comma-separated)
5. **Create Timestamp** (MM/DD/YYYY hh:mm:ss AM/PM)
6. **Deactivated Timestamp** (MM/DD/YYYY hh:mm:ss AM/PM, may be empty)

**Example:**
```
SRV-PLAT-01,Helpdesk,Platform,192.168.1.1,05/14/2025 08:30:00 AM,05/14/2025 05:00:00 PM
DT-DEVOPS-01,DevOps-Support,DevOps,192.168.2.10,192.168.2.11,192.168.2.12,05/11/2025 10:15:00 AM,
```

## What the App Does

- Reads the CSV file and parses each row.
- For each row:
  - If `deactivatedTimestamp` is empty, adds all IPs to the "active" sets for both owner and contact.
  - If `deactivatedTimestamp` is present, adds all IPs to the "deactivated" sets for both owner and contact.
- Optionally, if a start timestamp is provided as a command-line argument, only includes records where either the create or deactivated timestamp is after or equal to the start timestamp.
- If a start timestamp is not provided as an argument, the application attempts to read it from `CsvToApiConversion.txt` in the project root (using ISO format or `MM/dd/yyyy hh:mm:ss a`).
- Builds four maps:
  - `ownerToActiveIps`
  - `contactToActiveIps`
  - `ownerToDeactivatedIps`
  - `contactToDeactivatedIps`
- Makes Qualys API calls for each map entry (unless suppressed):
  - **Removals** (deactivated IPs) are processed before **additions** (active IPs).
  - Looks up the Qualys tag ID using the tag API, then edits the tag to add or remove IPs.
  - Adds the header `X-Requested-With: Java` to all API requests.
  - If the tag is not found and the action is `add`, attempts to create the tag and associate IPs.
  - If the tag cannot be created, logs and records a `TAG_NOT_FOUND_AND_CREATE_FAILED` error.
  - If an API call fails, logs the full request and response details.
  - If the API response contains a recognized error code, logs and records the code and description.
  - If the API response contains a fatal error code (`1901`, `1903`, `1904`, `1905`, `1907`, `1908`, `1920`, `1960`, `1965`, `1922`, `1981`, `999`, `1999`, `2000`, `2002`, `2003`, `2011`, `2012`), the application logs the error and exits immediately.
- If the third argument is set to `true`, API calls are suppressed and only dry-run output is printed.
- All summary output (maps and error records) is logged to both the logger and the console.
- **On successful completion, writes the application start timestamp (not the end time) to `CsvToApiConversion.txt` in the project root (overwriting any previous content).**

## Running the Application

```sh
java -jar target/csv-to-api-conversion-0.0.1-SNAPSHOT.jar [csvFilePath] [optionalStartTimestamp] [suppressApiCall]
```
- `csvFilePath` (optional): Path to the CSV file. Defaults to `src/main/resources/sample.csv` if not provided.
- `optionalStartTimestamp` (optional): Filter records to only include those with create or deactivated timestamps after this value. Format: `MM/dd/yyyy hh:mm:ss a` or ISO format if read from `CsvToApiConversion.txt`.
- `suppressApiCall` (optional): If `true`, API calls are not made and only dry-run output is printed. Defaults to `false`.

## Logging

- All output is logged to both the console and a file named `csv-to-api-conversion.log` in the working directory.
- Errors and API responses are logged with appropriate severity.

## Sample Output

```
Owner to Active IPs:
Owner: Platform -> IPs: [192.168.1.1]
Owner: DevOps -> IPs: [192.168.2.10, 192.168.2.11, 192.168.2.12]
...

Contact to Active IPs:
Contact: Helpdesk -> IPs: [192.168.1.1]
Contact: DevOps-Support -> IPs: [192.168.2.10, 192.168.2.11, 192.168.2.12]
...

Owner to Deactivated IPs:
Owner: Design -> Deactivated IPs: [192.168.4.10, 192.168.4.11, 192.168.4.12]
...

Contact to Deactivated IPs:
Contact: Design-Support -> Deactivated IPs: [192.168.4.10, 192.168.4.11, 192.168.4.12]
...

Error Records: [TAG_NOT_FOUND_AND_CREATE_FAILED:SomeTag, 1901: Unrecognized parameter(s)]
```

## Example `sample.csv`

```
SRV-PLAT-01,Helpdesk,Platform,192.168.1.1,05/14/2025 08:30:00 AM,05/14/2025 05:00:00 PM
SRV-QA-01,QA-Support,QA,10.0.0.1,05/13/2025 09:00:00 AM,05/13/2025 06:00:00 PM
DT-AN-01,Analytics-Support,Analytics,172.16.0.1,05/12/2025 07:45:00 AM,05/12/2025 04:30:00 PM
DT-DEVOPS-01,DevOps-Support,DevOps,192.168.2.10,192.168.2.11,192.168.2.12,05/11/2025 10:15:00 AM,
DT-FE-01,Frontend-Support,Frontend,192.168.3.10,192.168.3.11,192.168.3.12,05/11/2025 10:15:00 AM,
DT-DESIGN-01,Design-Support,Design,192.168.4.10,192.168.4.11,192.168.4.12,,04/11/2025 07:00:00 PM
DT-TEST-01,Testing-Support,Testing,192.168.5.10,192.168.5.11,192.168.5.12,,
LAP-BE-01,Backend-Support,Backend,192.168.6.10,192.168.6.11,192.168.6.12,03/22/2023 10:15:00 AM,05/11/2025 07:00:00 PM
LAP-SUPPORT-01,IT-Support,Support,203.0.113.5,2001:0db8:85a3:0000:0000:8a2e:0370:7334,05/15/2025 09:00:00 AM,
# --- Boundary test data below ---
SRV-PLAT-02,Helpdesk,Platform,10.10.10.1,10/01/2024 12:01:02 AM,10/02/2024 01:00:00 PM
SRV-QA-02,QA-Support,QA,10.10.10.2,09/30/2024 11:59:59 PM,10/01/2024 12:01:01 AM
DT-DEVOPS-02,DevOps-Support,DevOps,10.10.10.3,10/01/2024 12:01:02 AM,
DT-FE-02,Frontend-Support,Frontend,10.10.10.4,10/01/2024 12:01:01 AM,10/01/2024 12:01:02 AM
DT-AN-02,Analytics-Support,Analytics,10.10.10.5,10/01/2024 12:01:03 AM,
```

## Notes

- The application is designed for demonstration and can be extended for real Qualys API integration.
- All IP addresses in the sample data are unique.
- Qualys API credentials must be provided in the code for real API calls.
- The application adds the `X-Requested-With: Java` header to all Qualys API requests.
- If a start timestamp is provided, only records with create or deactivated timestamps after or equal to this value are processed.
- If `suppressApiCall` is set to `true`, no API calls are made and only dry-run output is printed.
- If the API response contains a fatal error code (`1901`, `1903`, `1904`, `1905`, `1907`, `1908`, `1920`, `1960`, `1965`, `1922`, `1981`, `999`, `1999`, `2000`, `2002`, `2003`, `2011`, `2012`), the application logs the error and exits immediately.
- **On successful completion, the application writes the application start timestamp to `CsvToApiConversion.txt` in the project root (overwriting any previous content).**
- All summary output is logged to both the logger and the console.

---