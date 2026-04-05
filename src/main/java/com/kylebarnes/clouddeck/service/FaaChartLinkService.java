package com.kylebarnes.clouddeck.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class FaaChartLinkService {
    private static final LocalDate AIRAC_BASE_DATE = LocalDate.of(2024, 12, 26);
    private static final int AIRAC_BASE_YEAR = 24;
    private static final int AIRAC_BASE_CYCLE = 13;

    public String buildAirportDiagramUrl(String airportId) {
        return "https://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/dtpp/search/results/?cycle="
                + currentAiracCycle()
                + "&ident="
                + encodedAirportSearchId(airportId);
    }

    public String buildChartSupplementUrl(String airportId) {
        return "https://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/dafd/search/results/?cycle="
                + currentAiracCycle()
                + "&ident="
                + encodedAirportSearchId(airportId)
                + "&navaid=";
    }

    public String airportSearchHint(String airportId) {
        String normalized = normalizeAirportId(airportId);
        String faaSearchId = faaSearchId(airportId);
        return normalized.equals(faaSearchId)
                ? "FAA search ID: " + normalized
                : "FAA search ID: " + faaSearchId + " (from " + normalized + ")";
    }

    String currentAiracCycle() {
        return currentAiracCycle(LocalDate.now(ZoneOffset.UTC));
    }

    String currentAiracCycle(LocalDate dateUtc) {
        if (dateUtc == null || dateUtc.isBefore(AIRAC_BASE_DATE)) {
            return String.format("%02d%02d", AIRAC_BASE_YEAR, AIRAC_BASE_CYCLE);
        }

        LocalDate cycleStart = AIRAC_BASE_DATE;
        int cycleYear = AIRAC_BASE_YEAR;
        int cycleNumber = AIRAC_BASE_CYCLE;

        while (!dateUtc.isBefore(cycleStart.plusDays(28))) {
            cycleStart = cycleStart.plusDays(28);
            int nextYear = cycleStart.getYear() % 100;
            if (nextYear != cycleYear) {
                cycleYear = nextYear;
                cycleNumber = 1;
            } else {
                cycleNumber += 1;
            }
        }

        return String.format("%02d%02d", cycleYear, cycleNumber);
    }

    String faaSearchId(String airportId) {
        String normalized = normalizeAirportId(airportId);
        if (normalized.length() == 4) {
            char prefix = normalized.charAt(0);
            if (prefix == 'K' || prefix == 'P' || prefix == 'C' || prefix == 'T') {
                return normalized.substring(1);
            }
        }
        return normalized;
    }

    private String normalizeAirportId(String airportId) {
        return airportId == null ? "" : airportId.trim().toUpperCase();
    }

    private String encodedAirportSearchId(String airportId) {
        return URLEncoder.encode(faaSearchId(airportId), StandardCharsets.UTF_8);
    }
}
