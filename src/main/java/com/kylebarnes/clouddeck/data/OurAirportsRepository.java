package com.kylebarnes.clouddeck.data;

import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.AirportSuggestion;
import com.kylebarnes.clouddeck.model.Runway;
import com.kylebarnes.clouddeck.util.SimpleCsvParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OurAirportsRepository {
    private static final String AIRPORTS_CSV_URL = "https://davidmegginson.github.io/ourairports-data/airports.csv";
    private static final String RUNWAYS_CSV_URL = "https://davidmegginson.github.io/ourairports-data/runways.csv";

    private final HttpClient httpClient;

    private volatile Map<String, AirportInfo> airportInfoByIdent;
    private volatile List<AirportSuggestion> airportSuggestions;
    private volatile Map<String, List<Runway>> runwaysByAirport;

    public OurAirportsRepository() {
        this(HttpClient.newHttpClient());
    }

    public OurAirportsRepository(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public AirportInfo findAirportByIcao(String icaoId) {
        if (icaoId == null || icaoId.isBlank()) {
            return null;
        }

        try {
            ensureAirportsLoaded();
            return airportInfoByIdent.get(icaoId.trim().toUpperCase(Locale.US));
        } catch (Exception exception) {
            System.out.println("Could not fetch airport info for " + icaoId + ": " + exception.getMessage());
            return null;
        }
    }

    public List<AirportSuggestion> suggestAirports(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            ensureAirportsLoaded();
            String normalizedQuery = query.trim().toUpperCase(Locale.US);

            return airportSuggestions.stream()
                    .filter(airport -> matchesAirportQuery(airport, normalizedQuery))
                    .sorted(Comparator.comparing(AirportSuggestion::ident))
                    .limit(maxResults)
                    .toList();
        } catch (Exception exception) {
            System.out.println("Autocomplete error: " + exception.getMessage());
            return List.of();
        }
    }

    public List<Runway> findRunways(String icaoId) {
        if (icaoId == null || icaoId.isBlank()) {
            return List.of();
        }

        try {
            ensureRunwaysLoaded();
            return new ArrayList<>(runwaysByAirport.getOrDefault(icaoId.trim().toUpperCase(Locale.US), List.of()));
        } catch (Exception exception) {
            System.out.println("Could not fetch runway data for " + icaoId + ": " + exception.getMessage());
            return List.of();
        }
    }

    private boolean matchesAirportQuery(AirportSuggestion airport, String query) {
        return airport.ident().toUpperCase(Locale.US).startsWith(query)
                || airport.name().toUpperCase(Locale.US).contains(query)
                || airport.municipality().toUpperCase(Locale.US).contains(query);
    }

    private synchronized void ensureAirportsLoaded() throws Exception {
        if (airportInfoByIdent != null && airportSuggestions != null) {
            return;
        }

        String csv = fetchCsv(AIRPORTS_CSV_URL);
        Map<String, AirportInfo> loadedAirportInfo = new LinkedHashMap<>();
        List<AirportSuggestion> loadedSuggestions = new ArrayList<>();

        String[] lines = csv.split("\\R");
        for (int index = 1; index < lines.length; index++) {
            List<String> columns = SimpleCsvParser.parseLine(lines[index]);
            if (columns.size() < 11) {
                continue;
            }

            String ident = clean(columns.get(1)).toUpperCase(Locale.US);
            if (ident.length() != 4) {
                continue;
            }

            String airportType = clean(columns.get(2));
            String name = clean(columns.get(3));
            String latitude = clean(columns.get(4));
            String longitude = clean(columns.get(5));
            String elevation = clean(columns.get(6));
            String isoRegion = clean(columns.get(9));
            String municipality = clean(columns.get(10));

            int elevationFt = -1;
            double latitudeDeg = 0;
            double longitudeDeg = 0;
            if (!elevation.isEmpty()) {
                try {
                    elevationFt = (int) Math.round(Double.parseDouble(elevation));
                } catch (NumberFormatException ignored) {
                }
            }
            if (!latitude.isEmpty()) {
                try {
                    latitudeDeg = Double.parseDouble(latitude);
                } catch (NumberFormatException ignored) {
                }
            }
            if (!longitude.isEmpty()) {
                try {
                    longitudeDeg = Double.parseDouble(longitude);
                } catch (NumberFormatException ignored) {
                }
            }

            loadedAirportInfo.put(ident, new AirportInfo(
                    ident,
                    name,
                    municipality,
                    isoRegion,
                    elevationFt,
                    airportType,
                    latitudeDeg,
                    longitudeDeg
            ));
            loadedSuggestions.add(new AirportSuggestion(ident, name, municipality));
        }

        airportInfoByIdent = loadedAirportInfo;
        airportSuggestions = List.copyOf(loadedSuggestions);
    }

    private synchronized void ensureRunwaysLoaded() throws Exception {
        if (runwaysByAirport != null) {
            return;
        }

        String csv = fetchCsv(RUNWAYS_CSV_URL);
        Map<String, List<Runway>> loadedRunways = new LinkedHashMap<>();

        String[] lines = csv.split("\\R");
        for (int index = 1; index < lines.length; index++) {
            List<String> columns = SimpleCsvParser.parseLine(lines[index]);
            if (columns.size() < 19) {
                continue;
            }

            String ident = clean(columns.get(2)).toUpperCase(Locale.US);
            String closed = clean(columns.get(7));
            if (ident.isBlank() || "1".equals(closed)) {
                continue;
            }

            addRunway(loadedRunways, ident, clean(columns.get(8)), clean(columns.get(12)));
            addRunway(loadedRunways, ident, clean(columns.get(14)), clean(columns.get(18)));
        }

        runwaysByAirport = loadedRunways;
    }

    private void addRunway(Map<String, List<Runway>> runwayMap, String airportIdent, String runwayIdent, String headingValue) {
        if (runwayIdent.isBlank() || headingValue.isBlank()) {
            return;
        }

        try {
            int heading = (int) Double.parseDouble(headingValue);
            runwayMap.computeIfAbsent(airportIdent, ignored -> new ArrayList<>()).add(new Runway(runwayIdent, heading));
        } catch (NumberFormatException ignored) {
        }
    }

    private String fetchCsv(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
