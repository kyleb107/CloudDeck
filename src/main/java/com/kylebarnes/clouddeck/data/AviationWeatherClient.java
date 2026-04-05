package com.kylebarnes.clouddeck.data;

import org.json.JSONArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AviationWeatherClient {
    private static final String METAR_URL = "https://aviationweather.gov/api/data/metar?ids=%s&format=json";

    private final HttpClient httpClient;

    public AviationWeatherClient() {
        this(HttpClient.newHttpClient());
    }

    public AviationWeatherClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public JSONArray fetchMetars(String airportIds) throws Exception {
        String url = String.format(METAR_URL, airportIds.toUpperCase());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONArray jsonArray = new JSONArray(response.body());
        if (jsonArray.isEmpty()) {
            throw new Exception("No METAR data found for: " + airportIds);
        }
        return jsonArray;
    }
}
