# CloudDeck

CloudDeck is a JavaFX desktop app for pilots that combines live FAA METAR data with runway analysis and route planning.

## Current Capabilities
- Live METAR retrieval from the FAA Aviation Weather API
- TAF retrieval and forecast group parsing from the FAA Aviation Weather API
- Flight category display with color-coded airport cards
- VFR minimums assessment based on visibility and ceiling
- Runway crosswind and headwind analysis using the OurAirports dataset
- Route planner with current plus forecast weather assessment
- Aircraft profiles with fuel, cruise, reserve, and crosswind planning inputs
- Direct-route fuel and endurance summary based on the selected aircraft
- Airport briefing details with field elevation and density altitude advisories
- App-wide settings for home airport, default aircraft, units, and planning thresholds
- Airport autocomplete backed by cached airport data
- Persistent favorites stored in a local CloudDeck app directory

## Stack
- Java 21
- JavaFX 21
- Maven
- `org.json`

## Run
```bash
mvn clean javafx:run
```

If you run from IntelliJ, use `com.kylebarnes.clouddeck.CloudDeckLauncher` as the main class.

## Project Layout
```text
src/main/java/com/kylebarnes/clouddeck/
├── cli/       # Legacy terminal entry point
├── data/      # FAA and OurAirports clients plus parsing
├── model/     # Immutable domain models
├── service/   # Flight rules, runway analysis, orchestration
├── storage/   # Favorites persistence abstraction
├── ui/        # JavaFX application
└── util/      # Shared utilities such as CSV parsing
```

## Notes
- Favorites are stored in `~/.clouddeck/favorites.txt`
- Aircraft profiles are stored in `~/.clouddeck/aircraft_profiles.tsv`
- App settings are stored in `~/.clouddeck/settings.properties`
- A legacy `~/clouddeck_favorites.txt` file is still read automatically for migration
- The desktop UI is now separated from the core services so the domain layer can be reused by a future web or mobile client

## Next Technical Priorities
- Add automated tests for service and parser classes
- Introduce TAF support and forecast-aware route decisions
- Add aircraft profiles for crosswind and fuel-planning features
