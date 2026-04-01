# CloudDeck

CloudDeck is a Java-based aviation weather tool for pilots that pulls live METAR data from the FAA Aviation Weather API. Supports multiple airports, crosswind component calculations, and real-time flight category display.

<img width="793" height="658" alt="Screenshot 2026-04-01 154504" src="https://github.com/user-attachments/assets/d60fc482-7f37-4d70-b760-ffdc59731484" />

<img width="776" height="655" alt="Screenshot 2026-04-01 154439" src="https://github.com/user-attachments/assets/bdb91fad-e85a-45e6-ad34-97649782a6ca" />

## Features
- **METAR Fetcher**: Retrieves real-time weather data from the FAA Aviation Weather API in JSON format.
- **METAR Parser**: Extracts key information such as wind direction, speed, altimeter setting, and flight category.
- **Crosswind Calculator**: Computes headwind/tailwind and crosswind components for specific runways.
- **Support for Multiple Stations**: Accepts multiple ICAO station IDs separated by commas.
- ***NEW*****Route Planner**: Plan a flight between two different airports based on Metar weather data.

## Requirements
- **Java Development Kit (JDK)**: Version 24 or higher (as specified in `pom.xml`).
- **Apache Maven**: For dependency management and building the project.
- **Internet Connection**: Required to fetch data from the FAA API.

## Setup
1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd AviationWeatherTracker
   ```
2. Build the project using Maven:
   ```bash
   mvn clean install
   ```

## Usage
Run the application using Maven:
```bash
mvn exec:java -Dexec.mainClass="Main"
```
Or run the compiled JAR (if packaged):
```bash
java -cp "target/classes;target/dependency/*" Main
```

### Example Interaction
1. **Enter ICAO station ID**: Type an airport ID (e.g., `KLAX` or `KJFK,KSFO`).
2. **View METAR**: The tool displays parsed data and the raw METAR string.
3. **Crosswind Calculation**:
   - Enter a runway heading (e.g., `25` for Runway 25).
   - The tool outputs the headwind/tailwind and crosswind components.
   - Enter `0` to skip or `quit` to exit the application.

## Scripts
- `mvn clean compile`: Compiles the source code.
- `mvn exec:java -Dexec.mainClass="Main"`: Runs the application.
- `mvn package`: Packages the application into a JAR file.

## Environment Variables
Currently, no environment variables are required.
- **TODO**: Add support for an API key if the FAA API ever requires authentication.

## Tests
- **TODO**: Implement unit tests for `MetarParser` and `CrosswindCalculator` using JUnit.

## Project Structure
```text
AviationWeatherTracker/
├── src/main/java/
│   ├── Main.java                # Application entry point & CLI logic
│   ├── MetarFetcher.java        # API communication (FAA Aviation Weather)
│   ├── MetarParser.java         # JSON parsing logic
│   ├── MetarData.java           # Data model for METAR information
│   └── CrosswindCalculator.java # Wind component math
├── pom.xml                      # Maven configuration & dependencies
└── AviationWeatherTracker.iml   # IntelliJ IDEA project file
```

## License
Created and designed by **Kyle Barnes**.
- **TODO**: Add a formal license (e.g., MIT, Apache 2.0) if intended for public distribution.
