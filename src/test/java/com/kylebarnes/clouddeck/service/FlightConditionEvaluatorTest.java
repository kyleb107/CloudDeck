package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.CloudLayer;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;
import com.kylebarnes.clouddeck.model.TemperatureUnit;
import com.kylebarnes.clouddeck.model.ThemePreset;
import com.kylebarnes.clouddeck.model.DistanceUnit;
import com.kylebarnes.clouddeck.model.TimedRouteAssessment;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightConditionEvaluatorTest {
    private final FlightConditionEvaluator evaluator = new FlightConditionEvaluator();

    @Test
    void assessVfrUsesConfiguredThresholds() {
        AppSettings settings = new AppSettings(
                "",
                "",
                ThemePreset.NIGHTFALL,
                TemperatureUnit.FAHRENHEIT,
                DistanceUnit.NAUTICAL_MILES,
                1.0,
                1.5,
                -5,
                3.0f,
                1000,
                5.0f,
                3000,
                3000,
                5000
        );

        MetarData metar = new MetarData(
                "KAAA",
                "Alpha",
                "KAAA 011200Z 14010KT 4SM BKN025 20/15 A2992",
                "2026-04-01T12:00:00Z",
                140,
                10,
                0,
                29.92f,
                "MVFR",
                List.of(new CloudLayer("BKN", 2500)),
                20.0f,
                4.0f
        );

        VfrAssessment assessment = evaluator.assessVfr(metar, settings);

        assertEquals(VfrStatusLevel.CAUTION, assessment.level());
    }

    @Test
    void assessTimedRouteFlagsDestinationForecastAtArrival() {
        AppSettings settings = AppSettings.defaults();
        LocalDateTime departureTime = LocalDateTime.of(2026, 4, 1, 12, 0);
        LocalDateTime arrivalTime = LocalDateTime.of(2026, 4, 1, 13, 30);

        AirportWeather departure = new AirportWeather(
                null,
                buildMetar("KAAA", 10.0f, List.of(new CloudLayer("SCT", 5000))),
                new TafData(
                        "KAAA",
                        "011130Z",
                        departureTime.minusMinutes(30),
                        "0112/0212",
                        departureTime.minusHours(1),
                        departureTime.plusHours(24),
                        "TAF KAAA",
                        List.of(new TafPeriod("FM011200", "FM", departureTime.minusHours(1), departureTime.plusHours(4), 180, 10, 0, 6.0f, List.of(new CloudLayer("SCT", 5000)), List.of(), "FM011200 18010KT P6SM SCT050"))
                ),
                List.of()
        );
        AirportWeather destination = new AirportWeather(
                null,
                buildMetar("KBBB", 10.0f, List.of(new CloudLayer("SCT", 6000))),
                new TafData(
                        "KBBB",
                        "011130Z",
                        departureTime.minusMinutes(30),
                        "0112/0212",
                        departureTime.minusHours(1),
                        departureTime.plusHours(24),
                        "TAF KBBB",
                        List.of(
                                new TafPeriod("FM011200", "FM", departureTime.minusHours(1), arrivalTime.minusMinutes(15), 180, 10, 0, 6.0f, List.of(new CloudLayer("SCT", 6000)), List.of(), "FM011200 18010KT P6SM SCT060"),
                                new TafPeriod("TEMPO011315", "TEMPO", arrivalTime.minusMinutes(15), arrivalTime.plusHours(2), 200, 12, 0, 2.0f, List.of(new CloudLayer("BKN", 800)), List.of("-RA"), "TEMPO 011315/011500 2SM -RA BKN008")
                        )
                ),
                List.of()
        );

        TimedRouteAssessment assessment = evaluator.assessTimedRoute(
                departure,
                destination,
                departureTime,
                arrivalTime,
                settings
        );

        assertEquals(RouteDecisionLevel.NO_GO, assessment.level());
        assertEquals(VfrStatusLevel.WARNING, assessment.destinationForecastAssessment().level());
    }

    private MetarData buildMetar(String airportId, float visibilitySm, List<CloudLayer> cloudLayers) {
        return new MetarData(
                airportId,
                airportId + " Airport",
                airportId + " RAW",
                "2026-04-01T12:00:00Z",
                180,
                10,
                0,
                29.95f,
                "VFR",
                cloudLayers,
                21.0f,
                visibilitySm
        );
    }
}
