AI Prompt Used to Stub Code

Create a new java app that will consume a csv file with six columns (defined below) and make an https api call for each row.

Use the following parameters to create the project:

Use Maven for Project Management
The Appname should be cmdb-cvs-conversion
Use Java Version 21
Do not use any dependencies. Only standard java

CSV Input Column Definitions
One=String
Two=String
Three=String
Four=Between one and many comma delimited IP Addresses
Five=DateTime format MM/DD/YYYY hh:mm:ss AM/PM
Six==DateTime format MM/DD/YYYY hh:mm:ss AM/PM


Sample Output
{
    "one": "John",
    "two": "Doe",
    "three": "Engineer",
    "ips": ["192.168.1.1"],
    "five": ""2025-05-14T08:30"",
    "six": ""2025-05-14T17:00""
}

{
    "one": "Jane",
    "two": "Smith",
    "three": "Manager",
    "ips": ["10.0.0.1"],
    "five": ""2025-05-13T09:00"",
    "six": ""2025-05-13T18:00""
}

{
    "one": "Alice",
    "two": "Brown",
    "three": "Analyst",
    "ips": ["172.16.0.1"],
    "five": ""2025-05-12T07:45"",
    "six": ""2025-05-12T16:30""
}

{
    "one": "Bob",
    "two": "White",
    "three": "Admin",
    "ips": ["192.168.1.10","192.168.1.11","192.168.1.12"],
    "five": ""2025-05-11T10:15"",
    "six": ""2025-05-11T19:00""
}

{
    "one": "Charlie",
    "two": "Green",
    "three": "Developer",
    "ips": ["192.168.1.10","192.168.1.11","192.168.1.12"],
    "five": ""2025-05-11T10:15"",
    "six": "null"
}

{
    "one": "Donna",
    "two": "Black",
    "ips": ["192.168.1.10","192.168.1.11","192.168.1.12"],
    "five": "null",
    "six": "null"
}