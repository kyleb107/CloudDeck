package com.kylebarnes.clouddeck.data;

import org.json.JSONArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public class AviationWeatherClient {
    private static final String METAR_URL = "https://aviationweather.gov/api/data/metar?ids=%s&format=json";
    private static final String METAR_HISTORY_URL = "https://aviationweather.gov/api/data/metar?ids=%s&format=json&hours=%d";
    private static final Duration METAR_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration HISTORY_CACHE_TTL = Duration.ofMinutes(20);

    private final HttpClient httpClient;
    private final LocalDataCache dataCache;

    public AviationWeatherClient() {
        this(HttpClient.newHttpClient(), new LocalDataCache());
    }

    public AviationWeatherClient(HttpClient httpClient) {
        this(httpClient, new LocalDataCache());
    }

    AviationWeatherClient(HttpClient httpClient, LocalDataCache dataCache) {
        this.httpClient = httpClient;
        this.dataCache = dataCache;
    }

    public JSONArray fetchMetars(String airportIds) throws Exception {
        String url = String.format(METAR_URL, airportIds.toUpperCase());
        return fetchMetarsFromUrl(url, airportIds, "metar-" + airportIds.toUpperCase(Locale.US), METAR_CACHE_TTL);
    }

    public JSONArray fetchMetarHistory(String airportIds, int hours) throws Exception {
        String url = String.format(METAR_HISTORY_URL, airportIds.toUpperCase(), hours);
        return fetchMetarsFromUrl(url, airportIds, "metar-history-" + airportIds.toUpperCase(Locale.US) + "-" + hours, HISTORY_CACHE_TTL);
    }

    private JSONArray fetchMetarsFromUrl(String url, String airportIds, String cacheKey, Duration ttl) throws Exception {
        String responseBody = dataCache.getText("aviationweather", cacheKey, ttl, () -> fetchText(url));
        JSONArray jsonArray = new JSONArray(responseBody);
        if (jsonArray.isEmpty()) {
            throw new Exception("No METAR data found for: " + airportIds);
        }
        return jsonArray;
    }

    private String fetchText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new Exception("FAA weather service returned status " + response.statusCode());
        }
        return response.body() == null ? "[]" : response.body();
    }
}
