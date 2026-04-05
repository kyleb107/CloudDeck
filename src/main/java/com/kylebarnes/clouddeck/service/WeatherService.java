package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.data.AviationWeatherClient;
import com.kylebarnes.clouddeck.data.MetarParser;
import com.kylebarnes.clouddeck.data.OurAirportsRepository;
import com.kylebarnes.clouddeck.data.TafClient;
import com.kylebarnes.clouddeck.data.TafParser;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.TafData;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
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

        Map<String, TafData> tafByAirport = tafParser.parseMany(
                tafClient.fetchRawTafs(String.join(",", airportIds))
        );

        metars.sort((left, right) ->
                Integer.compare(airportIds.indexOf(left.airportId()), airportIds.indexOf(right.airportId()))
        );

        List<AirportWeather> airportWeather = new ArrayList<>();
        for (MetarData metar : metars) {
            airportWeather.add(new AirportWeather(
                    metar,
                    tafByAirport.get(metar.airportId()),
                    airportsRepository.findRunways(metar.airportId())
            ));
        }
        return airportWeather;
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
}
