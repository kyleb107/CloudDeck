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
- Route assumptions for taxi fuel, climb fuel, and groundspeed adjustment
- Airport briefing details with field elevation and density altitude advisories
- App-wide settings for home airport, default aircraft, theme, units, and planning thresholds
- Time-aware route planning using planned departure time and forecast-relevant ETA checks
- Alternate-airport suggestions when route conditions are poor
- Sunrise and sunset planning in airport briefings and route summaries
- Operational alerts panel for fuel, weather, runway, and night-planning risks
- Exportable route briefings saved as text files
- FAA airport diagram and Chart Supplement links from each airport briefing
- Inline FAA airport diagram previews rendered inside airport briefings
- Recent-route history with one-click route reuse
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

## Test
```bash
mvn test
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
- Cached FAA airport diagrams are stored in `~/.clouddeck/charts/`
- Exported briefings are stored in `~/.clouddeck/briefings/`
- Recent routes are stored in `~/.clouddeck/recent_routes.tsv`
- A legacy `~/clouddeck_favorites.txt` file is still read automatically for migration
- The desktop UI is now separated from the core services so the domain layer can be reused by a future web or mobile client

## Next Technical Priorities
- Add local-time display and broader unit preferences
- Add printable or PDF briefing output alongside text export
- Add API endpoints around the existing service layer for a future web client
