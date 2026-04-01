/* Designed by: Kyle Barnes
   Fetches raw METAR JSON Weather Data from FAA Aviation Weather API
 */

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MetarFetcher {
    private static final String BASE_URL = "https://aviationweather.gov/api/data/metar?ids=%s&format=json";

    public JSONObject fetchRaw(String icaoId) throws Exception {
        String url = String.format(BASE_URL, icaoId.toUpperCase());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JSONArray jsonArray = new JSONArray(response.body());
        if (jsonArray.isEmpty()) {
            throw new Exception("No METAR data found for station: " + icaoId);
        }

        return jsonArray.getJSONObject(0);
    }
}
