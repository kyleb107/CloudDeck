/* Designed by: Kyle Barnes
   Provides ICAO airport autocomplete suggestions from the OurAirports dataset
 */

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class AirportSuggester {

    //===== CONSTANTS =====
    private static final String AIRPORTS_CSV_URL =
            "https://davidmegginson.github.io/ourairports-data/airports.csv";

    //===== CACHE =====
    private static List<String[]> airportCache = null;

    //===== LOAD =====
    //loads airports CSV and caches it — only downloads once per session
    private static List<String[]> getAirports() throws Exception {
        if (airportCache == null) {
            airportCache = new ArrayList<>();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AIRPORTS_CSV_URL))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            String[] lines = response.body().split("\n");

            //skip header row
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                String[] cols = line.split(",", -1);
                if (cols.length < 14) continue;

                //col 1 = ident (ICAO), col 3 = name, col 10 = municipality
                String ident = cols[1].replace("\"", "").trim();
                String name = cols[3].replace("\"", "").trim();
                String city = cols[10].replace("\"", "").trim();

                //only include airports with 4 letter ICAO codes
                if (ident.length() == 4) {
                    airportCache.add(new String[]{ident, name, city});
                }
            }
        }
        return airportCache;
    }

    //===== SUGGEST =====
    //returns up to maxResults airports matching the query
    public static List<String[]> suggest(String query, int maxResults) {
        List<String[]> results = new ArrayList<>();
        if (query == null || query.isEmpty()) return results;

        String q = query.toUpperCase().trim();

        try {
            for (String[] airport : getAirports()) {
                String ident = airport[0].toUpperCase();
                String name = airport[1].toUpperCase();
                String city = airport[2].toUpperCase();

                //match by ICAO prefix or name/city contains query
                if (ident.startsWith(q) || name.contains(q) || city.contains(q)) {
                    results.add(airport);
                    if (results.size() >= maxResults) break;
                }
            }
        }
        catch (Exception e) {
            System.out.println("Autocomplete error: " + e.getMessage());
        }

        return results;
    }
}