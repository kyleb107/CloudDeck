package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.CloudLayer;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.RoutePlan;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;
import com.kylebarnes.clouddeck.model.TimedRouteAssessment;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BriefingExportServiceTest {
    private final BriefingExportService briefingExportService = new BriefingExportService();

    @Test
    void buildRouteBriefingIncludesDecisionPlanningAndAlerts() {
        AirportInfo departureAirport = new AirportInfo("KAAA", "Alpha", "Alpha", "US-TX", 500, "small_airport", 32.0, -95.0);
        AirportInfo destinationAirport = new AirportInfo("KBBB", "Bravo", "Bravo", "US-TX", 700, "small_airport", 33.0, -96.0);
        AircraftProfile aircraft = new AircraftProfile("C172", 120.0, 10.0, 40.0, 8.0, 15.0, "");
        RoutePlan routePlan = new RoutePlan(departureAirport, destinationAirport, 120.0, 110.0, 1.1, 11.0, 1.0, 2.0, 14.0, 26.0, true);
        TimedRouteAssessment timedRouteAssessment = new TimedRouteAssessment(
                RouteDecisionLevel.CAUTION,
                "CAUTION - Marginal conditions appear at the planned departure or arrival time.",
                LocalDateTime.of(2026, 4, 1, 12, 0),
                LocalDateTime.of(2026, 4, 1, 13, 6),
                new VfrAssessment(VfrStatusLevel.VFR, "Forecast VFR at planned time"),
                new VfrAssessment(VfrStatusLevel.CAUTION, "Forecast marginal VFR at planned time")
        );

        String briefing = briefingExportService.buildRouteBriefing(
                routePlan,
                aircraft,
                List.of(
                        buildAirportWeather("KAAA", departureAirport),
                        buildAirportWeather("KBBB", destinationAirport)
                ),
                new RouteAssessment(RouteDecisionLevel.CAUTION, "CAUTION - Destination shows marginal current weather or forecast periods."),
                timedRouteAssessment,
                List.of(new OperationalAlert(VfrStatusLevel.CAUTION, "Night arrival", "KBBB planned arrival occurs after sunset or before sunrise.")),
                List.of(),
                AppSettings.defaults()
        );

        assertTrue(briefing.contains("CloudDeck Flight Briefing"));
        assertTrue(briefing.contains("Timed outlook: CAUTION"));
        assertTrue(briefing.contains("Taxi fuel: 1.0 gal"));
        assertTrue(briefing.contains("Night arrival"));
        assertTrue(briefing.contains("KAAA - Alpha Airport"));
        assertTrue(briefing.contains("Best runway"));
    }

    private AirportWeather buildAirportWeather(String airportId, AirportInfo airportInfo) {
        return new AirportWeather(
                airportInfo,
                new MetarData(
                        airportId,
                        airportInfo.name() + " Airport",
                        airportId + " RAW",
                        "2026-04-01T12:00:00Z",
                        180,
                        10,
                        0,
                        29.95f,
                        "VFR",
                        List.of(new CloudLayer("SCT", 5000)),
                        20.0f,
                        6.0f
                ),
                new TafData(
                        airportId,
                        "011130Z",
                        LocalDateTime.of(2026, 4, 1, 11, 30),
                        "0112/0212",
                        LocalDateTime.of(2026, 4, 1, 12, 0),
                        LocalDateTime.of(2026, 4, 2, 12, 0),
                        "TAF " + airportId,
                        List.of(new TafPeriod("FM011200", "FM", LocalDateTime.of(2026, 4, 1, 12, 0), LocalDateTime.of(2026, 4, 1, 18, 0), 180, 10, 0, 6.0f, List.of(new CloudLayer("SCT", 5000)), List.of(), "FM011200 18010KT P6SM SCT050"))
                ),
                List.of(new com.kylebarnes.clouddeck.model.Runway("18", 180)),
                List.of(),
                null
        );
    }
}
