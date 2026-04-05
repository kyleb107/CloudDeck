package com.kylebarnes.clouddeck.data;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TafClient {
    private static final String TAF_URL = "https://aviationweather.gov/api/data/taf?ids=%s&format=raw";

    private final HttpClient httpClient;

    public TafClient() {
        this(HttpClient.newHttpClient());
    }

    public TafClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String fetchRawTafs(String airportIds) throws Exception {
        String url = String.format(TAF_URL, airportIds.toUpperCase());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body() == null ? "" : response.body().trim();
    }
}
