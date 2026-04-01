/* Designed by: Kyle Barnes
   Fetches raw METAR JSON from the FAA Aviation Weather API
 */

import org.json.JSONArray;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MetarFetcher {

    //===== CONSTANTS =====
    //accepts comma-separated ICAO IDs ex: KDFW,KAUS,KLFK
    private static final String BASE_URL =
            "https://aviationweather.gov/api/data/metar?ids=%s&format=json";

    //===== FETCH =====
    public JSONArray fetchRaw(String icaoIds) throws Exception {
        String url = String.format(BASE_URL, icaoIds.toUpperCase());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JSONArray jsonArray = new JSONArray(response.body());
        if (jsonArray.isEmpty()) {
            throw new Exception("No METAR data found for: " + icaoIds);
        }

        return jsonArray;
    }
}