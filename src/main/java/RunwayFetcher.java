/* Designed by: Kyle Barnes
   Fetches runway headings using the OurAirports public CSV dataset
 */

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class RunwayFetcher {

    //ourairports runway CSV — free, no API key, updated daily
    private static final String RUNWAY_CSV_URL =
            "https://davidmegginson.github.io/ourairports-data/runways.csv";

    //cache the CSV so we only download it once per session
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

    public List<Runway> fetchRunways(String icaoId) {
        List<Runway> runways = new ArrayList<>();

        try {
            String csv = getRunwayCsv();
            String[] lines = csv.split("\n");

            //CSV columns: id,airport_ref,airport_ident,length_ft,width_ft,surface,
            //             lighted,closed,le_ident,le_latitude_deg,le_longitude_deg,
            //             le_elevation_ft,le_heading_degT,le_displaced_threshold_ft,
            //             he_ident,he_latitude_deg,he_longitude_deg,he_elevation_ft,
            //             he_heading_degT,he_displaced_threshold_ft

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                String[] cols = line.split(",", -1);
                if (cols.length < 19) continue;

                //col 2 is airport_ident
                String ident = cols[2].replace("\"", "").trim();
                if (!ident.equalsIgnoreCase(icaoId)) continue;

                //col 7 is closed — skip closed runways
                String closed = cols[7].replace("\"", "").trim();
                if (closed.equals("1")) continue;

                //col 8 is le_ident, col 12 is le_heading_degT
                String leIdent = cols[8].replace("\"", "").trim();
                String leHeadingStr = cols[12].replace("\"", "").trim();

                //col 14 is he_ident, col 18 is he_heading_degT
                String heIdent = cols[14].replace("\"", "").trim();
                String heHeadingStr = cols[18].replace("\"", "").trim();

                //add low end runway if valid
                if (!leIdent.isEmpty() && !leHeadingStr.isEmpty()) {
                    try {
                        int leHeading = (int) Double.parseDouble(leHeadingStr);
                        runways.add(new Runway(leIdent, leHeading));
                    }
                    catch (NumberFormatException ignored) {}
                }

                //add high end runway if valid
                if (!heIdent.isEmpty() && !heHeadingStr.isEmpty()) {
                    try {
                        int heHeading = (int) Double.parseDouble(heHeadingStr);
                        runways.add(new Runway(heIdent, heHeading));
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