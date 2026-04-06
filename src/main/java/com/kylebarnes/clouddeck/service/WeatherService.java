package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.data.AviationWeatherClient;
import com.kylebarnes.clouddeck.data.MetarParser;
import com.kylebarnes.clouddeck.data.OurAirportsRepository;
import com.kylebarnes.clouddeck.data.TafClient;
import com.kylebarnes.clouddeck.data.TafParser;
import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.TafData;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WeatherService {
    private final AviationWeatherClient aviationWeatherClient;
    private final TafClient tafClient;
    private final MetarParser metarParser;
    private final TafParser tafParser;
    private final OurAirportsRepository airportsRepository;

    public WeatherService(
            AviationWeatherClient aviationWeatherClient,
            TafClient tafClient,
            MetarParser metarParser,
            TafParser tafParser,
            OurAirportsRepository airportsRepository
    ) {
        this.aviationWeatherClient = aviationWeatherClient;
        this.tafClient = tafClient;
        this.metarParser = metarParser;
        this.tafParser = tafParser;
        this.airportsRepository = airportsRepository;
    }

    public List<AirportWeather> fetchAirportWeather(String input) throws Exception {
        List<String> airportIds = parseAirportIds(input);
        return fetchAirportWeather(airportIds);
    }

    public List<AirportWeather> fetchAirportWeather(List<String> airportIds) throws Exception {
        if (airportIds.isEmpty()) {
            throw new IllegalArgumentException("Please enter at least one ICAO airport ID.");
        }

        JSONArray results = aviationWeatherClient.fetchMetars(String.join(",", airportIds));
        List<MetarData> metars = new ArrayList<>();
        for (int index = 0; index < results.length(); index++) {
            metars.add(metarParser.parse(results.getJSONObject(index)));
        }

        List<String> returnedIds = metars.stream()
                .map(MetarData::airportId)
                .collect(Collectors.toList());
        List<String> missingIds = airportIds.stream()
                .filter(id -> !returnedIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new Exception("No METAR data returned for: " + String.join(", ", missingIds));
        }

        TafFetchResult tafFetchResult = fetchTafs(airportIds);

        metars.sort((left, right) ->
                Integer.compare(airportIds.indexOf(left.airportId()), airportIds.indexOf(right.airportId()))
        );

        List<AirportWeather> airportWeather = new ArrayList<>();
        for (MetarData metar : metars) {
            AirportInfo airportInfo = airportsRepository.findAirportByIcao(metar.airportId());
            airportWeather.add(new AirportWeather(
                    airportInfo,
                    metar,
                    tafFetchResult.tafByAirport().get(metar.airportId()),
                    airportsRepository.findRunways(metar.airportId()),
                    List.of(),
                    tafFetchResult.statusMessage()
            ));
        }
        return airportWeather;
    }

    public List<MetarData> fetchMetarHistory(String airportId, int hours) throws Exception {
        if (airportId == null || airportId.isBlank()) {
            return List.of();
        }

        String normalizedAirportId = airportId.trim().toUpperCase();
        return fetchHistoryByAirport(List.of(normalizedAirportId), hours)
                .getOrDefault(normalizedAirportId, List.of());
    }

    public List<String> parseAirportIds(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        Set<String> ids = new LinkedHashSet<>();
        Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(String::toUpperCase)
                .forEach(ids::add);
        return new ArrayList<>(ids);
    }

    private Map<String, List<MetarData>> fetchHistoryByAirport(List<String> airportIds, int hours) throws Exception {
        JSONArray historyResults = aviationWeatherClient.fetchMetarHistory(String.join(",", airportIds), hours);
        Map<String, List<MetarData>> historyByAirport = new HashMap<>();
        for (int index = 0; index < historyResults.length(); index++) {
            MetarData metar = metarParser.parse(historyResults.getJSONObject(index));
            historyByAirport.computeIfAbsent(metar.airportId(), ignored -> new ArrayList<>()).add(metar);
        }
        historyByAirport.replaceAll((airportId, history) -> history.stream()
                .sorted(Comparator.comparing(MetarData::observationTime))
                .toList());
        return historyByAirport;
    }

    private TafFetchResult fetchTafs(List<String> airportIds) {
        try {
            return new TafFetchResult(
                    tafParser.parseMany(tafClient.fetchRawTafs(String.join(",", airportIds))),
                    null
            );
        } catch (Exception exception) {
            return new TafFetchResult(
                    Map.of(),
                    "Could not load TAF data: " + exception.getMessage()
            );
        }
    }

    private record TafFetchResult(
            Map<String, TafData> tafByAirport,
            String statusMessage
    ) {
    }
}
