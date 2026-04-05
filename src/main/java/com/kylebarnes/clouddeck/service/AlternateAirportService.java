package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.data.OurAirportsRepository;
import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AlternateAirportOption;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.AirportWeather;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AlternateAirportService {
    private final OurAirportsRepository airportsRepository;
    private final WeatherService weatherService;
    private final FlightConditionEvaluator flightConditionEvaluator;
    private final RunwayAnalysisService runwayAnalysisService;

    public AlternateAirportService(
            OurAirportsRepository airportsRepository,
            WeatherService weatherService,
            FlightConditionEvaluator flightConditionEvaluator,
            RunwayAnalysisService runwayAnalysisService
    ) {
        this.airportsRepository = airportsRepository;
        this.weatherService = weatherService;
        this.flightConditionEvaluator = flightConditionEvaluator;
        this.runwayAnalysisService = runwayAnalysisService;
    }

    public List<AlternateAirportOption> suggestAlternates(
            String departureId,
            String destinationId,
            AircraftProfile aircraftProfile,
            AppSettings settings,
            int maxResults
    ) throws Exception {
        AirportInfo destinationAirport = airportsRepository.findAirportByIcao(destinationId);
        if (destinationAirport == null) {
            return List.of();
        }

        List<AirportInfo> nearbyAirports = airportsRepository.findNearbyAirports(destinationId, maxResults * 3, 120);
        List<String> candidateIds = nearbyAirports.stream()
                .map(AirportInfo::ident)
                .filter(ident -> !ident.equalsIgnoreCase(destinationId))
                .filter(ident -> !ident.equalsIgnoreCase(departureId))
                .limit(maxResults * 2L)
                .toList();

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        List<AirportWeather> weather = weatherService.fetchAirportWeather(candidateIds);
        List<AlternateAirportOption> options = new ArrayList<>();
        for (AirportWeather airportWeather : weather) {
            AirportInfo airportInfo = airportWeather.airportInfo();
            if (airportInfo == null) {
                continue;
            }

            double distanceNm = airportsRepository.distanceNm(destinationAirport, airportInfo);
            VfrAssessment vfrAssessment = flightConditionEvaluator.assessVfr(airportWeather.metar(), settings);
            boolean hasUsableRunway = runwayAnalysisService.analyze(
                    airportWeather.metar(),
                    airportWeather.runways(),
                    aircraftProfile
            ).stream().anyMatch(analysis -> !analysis.exceedsAircraftLimit());

            String summary = hasUsableRunway
                    ? "VFR check " + vfrAssessment.level() + " with at least one runway inside aircraft limits."
                    : "VFR check " + vfrAssessment.level() + " but runway crosswind limits are restrictive.";

            options.add(new AlternateAirportOption(
                    airportWeather,
                    distanceNm,
                    vfrAssessment,
                    hasUsableRunway,
                    summary
            ));
        }

        return options.stream()
                .sorted(Comparator
                        .comparing((AlternateAirportOption option) -> rank(option, aircraftProfile)).reversed()
                        .thenComparingDouble(AlternateAirportOption::distanceFromDestinationNm))
                .limit(maxResults)
                .toList();
    }

    private int rank(AlternateAirportOption option, AircraftProfile aircraftProfile) {
        int score = switch (option.vfrAssessment().level()) {
            case VFR -> 100;
            case CAUTION -> 60;
            case WARNING -> 10;
        };

        if (aircraftProfile != null && option.hasUsableRunway()) {
            score += 25;
        }
        if (aircraftProfile != null && !option.hasUsableRunway()) {
            score -= 40;
        }

        score -= (int) Math.round(option.distanceFromDestinationNm() / 4.0);
        return score;
    }
}
