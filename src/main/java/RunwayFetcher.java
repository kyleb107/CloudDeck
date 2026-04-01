/* Designed by: Kyle Barnes
   Fetches runway headings from the OurAirports public CSV dataset
 */

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class RunwayFetcher {

    //===== CONSTANTS =====
    //free, no API key required, updated daily by OurAirports
    private static final String RUNWAY_CSV_URL =
            "https://davidmegginson.github.io/ourairports-data/runways.csv";

    //===== CACHE =====
    //store CSV in memory so we only download it once per session (~4MB)
    private static String csvCache = null;

    private static String getRunwayCsv() throws Exception {
        if (csvCache == null) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RUNWAY_CSV_URL))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            csvCache = response.body();
        }
        return csvCache;
    }

    //===== FETCH RUNWAYS =====
    //CSV column reference:
    //col 2  = airport_ident
    //col 7  = closed (1 = closed, skip)
    //col 8  = le_ident       col 12 = le_heading_degT
    //col 14 = he_ident       col 18 = he_heading_degT
    public List<Runway> fetchRunways(String icaoId) {
        List<Runway> runways = new ArrayList<>();

        try {
            String csv = getRunwayCsv();
            String[] lines = csv.split("\n");

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                String[] cols = line.split(",", -1);
                if (cols.length < 19) continue;

                //skip if not the requested airport
                String ident = cols[2].replace("\"", "").trim();
                if (!ident.equalsIgnoreCase(icaoId)) continue;

                //skip closed runways
                String closed = cols[7].replace("\"", "").trim();
                if (closed.equals("1")) continue;

                //parse low end runway
                String leIdent = cols[8].replace("\"", "").trim();
                String leHeadingStr = cols[12].replace("\"", "").trim();
                if (!leIdent.isEmpty() && !leHeadingStr.isEmpty()) {
                    try {
                        runways.add(new Runway(leIdent, (int) Double.parseDouble(leHeadingStr)));
                    }
                    catch (NumberFormatException ignored) {}
                }

                //parse high end runway
                String heIdent = cols[14].replace("\"", "").trim();
                String heHeadingStr = cols[18].replace("\"", "").trim();
                if (!heIdent.isEmpty() && !heHeadingStr.isEmpty()) {
                    try {
                        runways.add(new Runway(heIdent, (int) Double.parseDouble(heHeadingStr)));
                    }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        catch (Exception e) {
            System.out.println("Could not fetch runway data for " + icaoId + ": " + e.getMessage());
        }

        return runways;
    }
}