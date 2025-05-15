# cmdb-cvs-conversion

## Overview
This project is a Spring Boot application that processes a CSV file containing six columns. For each row in the CSV, the application makes an HTTPS API call to perform specific operations.

## Project Structure
```
cmdb-cvs-conversion
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── example
│   │   │           └── cmdbcvsconversion
│   │   │               ├── CmdbCvsConversionApplication.java
│   │   │               ├── model
│   │   │               │   └── CsvRow.java
│   │   │               ├── service
│   │   │               │   ├── CsvProcessorService.java
│   │   │               │   └── ApiService.java
│   │   │               └── util
│   │   │                   └── CsvReader.java
│   │   └── resources
│   │       ├── application.properties
│   │       └── sample.csv
│   └── test
│       └── java
│           └── com
│               └── example
│                   └── cmdbcvsconversion
│                       └── CmdbCvsConversionApplicationTests.java
├── pom.xml
└── README.md
```

## Setup Instructions
1. **Clone the Repository**
   ```bash
   git clone <repository-url>
   cd cmdb-cvs-conversion
   ```

2. **Build the Project**
   Ensure you have Maven installed. Run the following command to build the project:
   ```bash
   mvn clean install
   ```

3. **Run the Application**
   You can run the application using the following command:
   ```bash
   mvn spring-boot:run
   ```

## Usage
- Place your CSV file in the `src/main/resources` directory or modify the `CsvReader` class to point to your desired CSV file location.
- The application will read the CSV file, process each row, and make the corresponding HTTPS API calls.

## Dependencies
This project uses the following dependencies:
- Spring Boot
- Spring Web
- Spring Security
- Spring Data JPA
- H2 Database

## Contributing
Feel free to submit issues or pull requests for any improvements or bug fixes. 

## License
This project is licensed under the MIT License.