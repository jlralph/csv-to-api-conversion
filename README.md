# cmdb-cvs-conversion

A Java 21 application (no external dependencies) that reads a CSV file, parses each row, and (optionally) makes Qualys API calls to manage asset groups. The app builds summary maps of owners and contacts to their associated IP addresses, separated by active and deactivated status.

## Project Setup

- **Build Tool:** Maven
- **Java Version:** 21
- **Dependencies:** None (uses only standard Java libraries)

## CSV Input Format

Each row in the CSV should have the following columns:

1. **Asset Name** (String)
2. **Contact** (String)
3. **Owner** (String)
4. **IP Addresses** (one or more, comma-separated)
5. **Create Timestamp** (MM/DD/YYYY hh:mm:ss AM/PM)
6. **Deactivated Timestamp** (MM/DD/YYYY hh:mm:ss AM/PM, may be empty)

**Example:**
```
Server1,Doe,Engineer,192.168.1.1,05/14/2025 08:30:00 AM,05/14/2025 05:00:00 PM
Desktop2,White,Admin,192.168.2.10,192.168.2.11,192.168.2.12,05/11/2025 10:15:00 AM,
```

## What the App Does

- Reads the CSV file and parses each row.
- For each row:
  - If `deactivatedTimestamp` is empty, adds all IPs to the "active" sets for both owner and contact.
  - If `deactivatedTimestamp` is present, adds all IPs to the "deactivated" sets for both owner and contact.
- Builds four maps:
  - `ownerToActiveIps`
  - `contactToActiveIps`
  - `ownerToDeactivatedIps`
  - `contactToDeactivatedIps`
- Iterates over each map and makes Qualys API calls to add or remove IPs from asset groups, using the group name as the owner or contact value.
- Looks up the Qualys asset group ID using the fo/asset/group API, then edits the group to add or remove IPs.
- Parses API responses for error codes and adds recognized codes and descriptions to the error records.
- Prints summary of all maps and error records.

## Sample Output

```
Owner to Active IPs:
Owner: Engineer -> IPs: [192.168.1.1]
Owner: Admin -> IPs: [192.168.2.10, 192.168.2.11, 192.168.2.12]
...

Contact to Active IPs:
Contact: Doe -> IPs: [192.168.1.1]
Contact: White -> IPs: [192.168.2.10, 192.168.2.11, 192.168.2.12]
...

Owner to Deactivated IPs:
Owner: Designer -> Deactivated IPs: [192.168.4.10, 192.168.4.11, 192.168.4.12]
...

Contact to Deactivated IPs:
Contact: Black -> Deactivated IPs: [192.168.4.10, 192.168.4.11, 192.168.4.12]
...

Error Records: [INVALID_IP: The IP address provided is invalid, GROUP_NOT_FOUND:SomeGroup]
```

## Example `sample.csv`

```
Server1,Doe,Engineer,192.168.1.1,05/14/2025 08:30:00 AM,05/14/2025 05:00:00 PM
Server2,Smith,Manager,10.0.0.1,05/13/2025 09:00:00 AM,05/13/2025 06:00:00 PM
Desktop1,Brown,Analyst,172.16.0.1,05/12/2025 07:45:00 AM,05/12/2025 04:30:00 PM
Desktop2,White,Admin,192.168.2.10,192.168.2.11,192.168.2.12,05/11/2025 10:15:00 AM,
Desktop3,Green,Developer,192.168.3.10,192.168.3.11,192.168.3.12,05/11/2025 10:15:00 AM,
Desktop4,Black,Designer,192.168.4.10,192.168.4.11,192.168.4.12,,05/11/2025 07:00:00 PM
Desktop5,White,Tester,192.168.5.10,192.168.5.11,192.168.5.12,,
Gary's PC,Gary,Development,192.168.6.10,192.168.6.11,192.168.6.12,05/11/2025 10:15:00 AM,05/11/2025 07:00:00 PM
Laptop1,Lee,Support,203.0.113.5,2001:0db8:85a3:0000:0000:8a2e:0370:7334,05/15/2025 09:00:00 AM,
ExtraRow,Kim,Ops,198.51.100.10,2001:db8:abcd:0012::1,05/16/2025 08:00:00 AM,
```

## Notes

- The application is designed for demonstration and can be extended for real Qualys API integration.
- All IP addresses in the sample data are unique.
- No external libraries are used; only Java standard library.
- Qualys API credentials must be provided in the code for real API calls.

---