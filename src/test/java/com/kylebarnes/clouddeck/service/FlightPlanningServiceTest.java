package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.DistanceUnit;
import com.kylebarnes.clouddeck.model.RoutePlan;
import com.kylebarnes.clouddeck.model.TemperatureUnit;
import com.kylebarnes.clouddeck.model.ThemePreset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightPlanningServiceTest {
    private final FlightPlanningService flightPlanningService = new FlightPlanningService();

    @Test
    void planDirectRouteAppliesRouteAssumptions() {
        AirportInfo departure = new AirportInfo("KAAA", "Alpha", "Alpha City", "US-TX", 500, "small_airport", 32.0, -95.0);
        AirportInfo destination = new AirportInfo("KBBB", "Bravo", "Bravo City", "US-TX", 700, "small_airport", 33.0, -96.0);
        AircraftProfile aircraft = new AircraftProfile("C172", 120.0, 10.0, 40.0, 8.0, 15.0, "");
        AppSettings settings = new AppSettings(
                "KAAA",
                "C172",
                ThemePreset.NIGHTFALL,
                TemperatureUnit.FAHRENHEIT,
                DistanceUnit.NAUTICAL_MILES,
                1.0,
                2.0,
                -10,
                3.0f,
                1000,
                5.0f,
                3000,
                3000,
                5000
        );

        RoutePlan routePlan = flightPlanningService.planDirectRoute(departure, destination, aircraft, settings);

        assertNotNull(routePlan);
        assertEquals(110.0, routePlan.groundspeedKts(), 0.01);
        assertEquals(routePlan.airborneFuelGallons() + 3.0, routePlan.tripFuelGallons(), 0.01);
        assertEquals(40.0 - routePlan.tripFuelGallons(), routePlan.reserveRemainingGallons(), 0.01);
        assertTrue(routePlan.estimatedTimeHours() > 0);
    }
}
