package com.kylebarnes.clouddeck.data;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public class TafClient {
    private static final String TAF_URL = "https://aviationweather.gov/api/data/taf?ids=%s&format=raw";
    private static final Duration TAF_CACHE_TTL = Duration.ofMinutes(30);

    private final HttpClient httpClient;
    private final LocalDataCache dataCache;

    public TafClient() {
        this(HttpClient.newHttpClient(), new LocalDataCache());
    }

    public TafClient(HttpClient httpClient) {
        this(httpClient, new LocalDataCache());
    }

    TafClient(HttpClient httpClient, LocalDataCache dataCache) {
        this.httpClient = httpClient;
        this.dataCache = dataCache;
    }

    public String fetchRawTafs(String airportIds) throws Exception {
        String url = String.format(TAF_URL, airportIds.toUpperCase());
        return dataCache.getText("taf", "taf-" + airportIds.toUpperCase(Locale.US), TAF_CACHE_TTL, () -> fetchText(url)).trim();
    }

    private String fetchText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new Exception("FAA TAF service returned status " + response.statusCode());
        }
        return response.body() == null ? "" : response.body();
    }
}
