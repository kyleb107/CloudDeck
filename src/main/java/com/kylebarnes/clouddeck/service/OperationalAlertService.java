package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.RoutePlan;
import com.kylebarnes.clouddeck.model.TimedRouteAssessment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OperationalAlertService {
    private final FlightConditionEvaluator flightConditionEvaluator = new FlightConditionEvaluator();

    public List<OperationalAlert> buildRouteAlerts(
            RoutePlan routePlan,
            AircraftProfile aircraftProfile,
            AirportWeather departureWeather,
            AirportWeather destinationWeather,
            List<RunwayAnalysis> departureRunwayAnalysis,
            List<RunwayAnalysis> destinationRunwayAnalysis,
            RouteAssessment routeAssessment,
            TimedRouteAssessment timedRouteAssessment,
            boolean departureInDaylight,
            boolean arrivalInDaylight,
            boolean alternateSuggested,
            AppSettings appSettings
    ) {
        List<OperationalAlert> alerts = new ArrayList<>();

        if (timedRouteAssessment != null && timedRouteAssessment.level() != RouteDecisionLevel.GO) {
            alerts.add(new OperationalAlert(
                    mapDecisionLevel(timedRouteAssessment.level()),
                    "Timed weather window",
                    timedRouteAssessment.message()
            ));
        } else if (routeAssessment != null && routeAssessment.level() != RouteDecisionLevel.GO) {
            alerts.add(new OperationalAlert(
                    mapDecisionLevel(routeAssessment.level()),
                    "Route weather outlook",
                    routeAssessment.message()
            ));
        }

        addAirportWeatherAlert(alerts, "Departure weather", departureWeather, appSettings);
        addAirportWeatherAlert(alerts, "Destination weather", destinationWeather, appSettings);

        if (timedRouteAssessment != null) {
            addForecastAlert(alerts, "Departure forecast", timedRouteAssessment.departureForecastAssessment());
            addForecastAlert(alerts, "Destination forecast", timedRouteAssessment.destinationForecastAssessment());
        }

        if (routePlan != null && aircraftProfile != null && !routePlan.reserveSatisfied()) {
            alerts.add(new OperationalAlert(
                    VfrStatusLevel.WARNING,
                    "Fuel reserve shortfall",
                    aircraftProfile.name() + " lands with " + formatOneDecimal(routePlan.reserveRemainingGallons())
                            + " gal remaining versus a " + formatOneDecimal(aircraftProfile.reserveFuelGallons()) + " gal target."
            ));
        }

        addRunwayAlert(alerts, "Departure runway", departureRunwayAnalysis, aircraftProfile);
        addRunwayAlert(alerts, "Destination runway", destinationRunwayAnalysis, aircraftProfile);

        if (routePlan != null && !departureInDaylight) {
            alerts.add(new OperationalAlert(
                    VfrStatusLevel.CAUTION,
                    "Night departure",
                    routePlan.departureAirport().ident() + " planned departure occurs after sunset or before sunrise."
            ));
        }
        if (routePlan != null && !arrivalInDaylight) {
            alerts.add(new OperationalAlert(
                    VfrStatusLevel.CAUTION,
                    "Night arrival",
                    routePlan.destinationAirport().ident() + " planned arrival occurs after sunset or before sunrise."
            ));
        }

        if (alternateSuggested) {
            alerts.add(new OperationalAlert(
                    VfrStatusLevel.CAUTION,
                    "Alternates recommended",
                    "Nearby alternate airports were found because the destination outlook is not fully GO."
            ));
        }

        alerts.sort(Comparator.comparingInt(alert -> severityRank(alert.level())));
        return alerts;
    }

    private void addAirportWeatherAlert(List<OperationalAlert> alerts, String title, AirportWeather weather, AppSettings appSettings) {
        if (weather == null) {
            return;
        }
        VfrAssessment assessment = flightConditionEvaluator.assessVfr(weather.metar(), appSettings);
        if (assessment.level() == VfrStatusLevel.VFR) {
            return;
        }
        alerts.add(new OperationalAlert(
                assessment.level(),
                title,
                weather.metar().airportId() + " is currently " + assessment.message().replace("CAUTION: ", "").replace("WARNING: ", "")
        ));
    }

    private void addForecastAlert(List<OperationalAlert> alerts, String title, VfrAssessment assessment) {
        if (assessment == null || assessment.level() == VfrStatusLevel.VFR) {
            return;
        }
        alerts.add(new OperationalAlert(assessment.level(), title, assessment.message()));
    }

    private void addRunwayAlert(
            List<OperationalAlert> alerts,
            String title,
            List<RunwayAnalysis> runwayAnalysis,
            AircraftProfile aircraftProfile
    ) {
        if (aircraftProfile == null || runwayAnalysis == null || runwayAnalysis.isEmpty()) {
            return;
        }
        RunwayAnalysis bestRunway = runwayAnalysis.getFirst();
        if (!bestRunway.exceedsAircraftLimit()) {
            return;
        }
        alerts.add(new OperationalAlert(
                VfrStatusLevel.WARNING,
                title,
                "Best runway " + bestRunway.runway().ident() + " still exceeds the selected aircraft crosswind limit of "
                        + formatOneDecimal(aircraftProfile.maxCrosswindKts()) + " kt."
        ));
    }

    private VfrStatusLevel mapDecisionLevel(RouteDecisionLevel level) {
        return switch (level) {
            case GO -> VfrStatusLevel.VFR;
            case CAUTION -> VfrStatusLevel.CAUTION;
            case NO_GO -> VfrStatusLevel.WARNING;
        };
    }

    private int severityRank(VfrStatusLevel level) {
        return switch (level) {
            case WARNING -> 0;
            case CAUTION -> 1;
            case VFR -> 2;
        };
    }

    private String formatOneDecimal(double value) {
        return String.format("%.1f", value);
    }
}
