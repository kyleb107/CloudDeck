package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AlternateAirportOption;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.RoutePlan;
import com.kylebarnes.clouddeck.model.SolarTimes;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;
import com.kylebarnes.clouddeck.model.TimedRouteAssessment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BriefingExportService {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FlightConditionEvaluator flightConditionEvaluator = new FlightConditionEvaluator();
    private final RunwayAnalysisService runwayAnalysisService = new RunwayAnalysisService();
    private final SolarCalculatorService solarCalculatorService = new SolarCalculatorService();

    public Path exportRouteBriefing(
            RoutePlan routePlan,
            AircraftProfile aircraftProfile,
            List<AirportWeather> routeWeather,
            RouteAssessment routeAssessment,
            TimedRouteAssessment timedRouteAssessment,
            List<OperationalAlert> alerts,
            List<AlternateAirportOption> alternates,
            AppSettings appSettings
    ) throws IOException {
        Files.createDirectories(defaultBriefingDirectory());

        String departureId = routePlan == null ? "ROUTE" : routePlan.departureAirport().ident();
        String destinationId = routePlan == null ? "BRIEFING" : routePlan.destinationAirport().ident();
        String filename = "clouddeck-briefing-" + departureId + "-" + destinationId + "-" + LocalDateTime.now(ZoneOffset.UTC).format(FILE_STAMP) + ".txt";
        Path outputPath = defaultBriefingDirectory().resolve(filename);
        Files.writeString(
                outputPath,
                buildRouteBriefing(routePlan, aircraftProfile, routeWeather, routeAssessment, timedRouteAssessment, alerts, alternates, appSettings),
                StandardCharsets.UTF_8
        );
        return outputPath;
    }

    public String buildRouteBriefing(
            RoutePlan routePlan,
            AircraftProfile aircraftProfile,
            List<AirportWeather> routeWeather,
            RouteAssessment routeAssessment,
            TimedRouteAssessment timedRouteAssessment,
            List<OperationalAlert> alerts,
            List<AlternateAirportOption> alternates,
            AppSettings appSettings
    ) {
        StringBuilder briefing = new StringBuilder();
        LocalDateTime generatedAtUtc = LocalDateTime.now(ZoneOffset.UTC);

        briefing.append("CloudDeck Flight Briefing").append(System.lineSeparator());
        briefing.append("Generated UTC: ").append(generatedAtUtc.format(TIME_FORMATTER)).append(System.lineSeparator());
        briefing.append(System.lineSeparator());

        briefing.append("[Route Decision]").append(System.lineSeparator());
        if (timedRouteAssessment != null) {
            briefing.append("Timed outlook: ").append(timedRouteAssessment.level()).append(" - ").append(timedRouteAssessment.message()).append(System.lineSeparator());
            briefing.append("Departure UTC: ").append(timedRouteAssessment.departureTimeUtc().format(TIME_FORMATTER)).append(System.lineSeparator());
            briefing.append("Arrival UTC: ").append(timedRouteAssessment.arrivalTimeUtc().format(TIME_FORMATTER)).append(System.lineSeparator());
        } else if (routeAssessment != null) {
            briefing.append("Route outlook: ").append(routeAssessment.level()).append(" - ").append(routeAssessment.message()).append(System.lineSeparator());
        } else {
            briefing.append("Route outlook unavailable.").append(System.lineSeparator());
        }
        briefing.append(System.lineSeparator());

        briefing.append("[Aircraft and Planning]").append(System.lineSeparator());
        if (aircraftProfile == null) {
            briefing.append("No aircraft profile selected. Fuel and runway-limit planning are omitted.").append(System.lineSeparator());
        } else {
            briefing.append(aircraftProfile.name())
                    .append(" | Cruise ").append(formatOneDecimal(aircraftProfile.cruiseSpeedKts())).append(" kt")
                    .append(" | Burn ").append(formatOneDecimal(aircraftProfile.fuelBurnGph())).append(" gph")
                    .append(" | Reserve ").append(formatOneDecimal(aircraftProfile.reserveFuelGallons())).append(" gal")
                    .append(System.lineSeparator());
        }
        if (routePlan != null) {
            briefing.append("Distance: ").append(formatOneDecimal(routePlan.distanceNm())).append(" nm").append(System.lineSeparator());
            briefing.append("Groundspeed: ").append(formatOneDecimal(routePlan.groundspeedKts())).append(" kt").append(System.lineSeparator());
            briefing.append("ETE: ").append(formatDuration(routePlan.estimatedTimeHours())).append(System.lineSeparator());
            briefing.append("Airborne fuel: ").append(formatOneDecimal(routePlan.airborneFuelGallons())).append(" gal").append(System.lineSeparator());
            briefing.append("Taxi fuel: ").append(formatOneDecimal(routePlan.taxiFuelGallons())).append(" gal").append(System.lineSeparator());
            briefing.append("Climb fuel: ").append(formatOneDecimal(routePlan.climbFuelGallons())).append(" gal").append(System.lineSeparator());
            briefing.append("Trip fuel: ").append(formatOneDecimal(routePlan.tripFuelGallons())).append(" gal").append(System.lineSeparator());
            briefing.append("Reserve remaining: ").append(formatOneDecimal(routePlan.reserveRemainingGallons())).append(" gal").append(System.lineSeparator());
        }
        briefing.append("Theme preset: ").append(appSettings.themePreset().displayName()).append(System.lineSeparator());
        briefing.append(System.lineSeparator());

        briefing.append("[Operational Alerts]").append(System.lineSeparator());
        if (alerts == null || alerts.isEmpty()) {
            briefing.append("No additional operational alerts.").append(System.lineSeparator());
        } else {
            for (OperationalAlert alert : alerts) {
                briefing.append("- ").append(alert.level()).append(": ").append(alert.title()).append(" - ").append(alert.detail()).append(System.lineSeparator());
            }
        }
        briefing.append(System.lineSeparator());

        briefing.append("[Airport Details]").append(System.lineSeparator());
        for (AirportWeather airportWeather : routeWeather) {
            appendAirportSection(briefing, airportWeather, aircraftProfile, appSettings, routePlan, timedRouteAssessment);
        }

        briefing.append("[Alternates]").append(System.lineSeparator());
        if (alternates == null || alternates.isEmpty()) {
            briefing.append("No alternate suggestions included.").append(System.lineSeparator());
        } else {
            for (AlternateAirportOption alternate : alternates) {
                briefing.append("- ")
                        .append(alternate.airportWeather().metar().airportId())
                        .append(" | ")
                        .append(formatOneDecimal(alternate.distanceFromDestinationNm()))
                        .append(" nm | ")
                        .append(alternate.summary())
                        .append(System.lineSeparator());
            }
        }

        return briefing.toString();
    }

    private void appendAirportSection(
            StringBuilder briefing,
            AirportWeather airportWeather,
            AircraftProfile aircraftProfile,
            AppSettings appSettings,
            RoutePlan routePlan,
            TimedRouteAssessment timedRouteAssessment
    ) {
        briefing.append(System.lineSeparator());
        briefing.append(airportWeather.metar().airportId())
                .append(" - ")
                .append(airportWeather.metar().airportName())
                .append(System.lineSeparator());

        VfrAssessment vfrAssessment = flightConditionEvaluator.assessVfr(airportWeather.metar(), appSettings);
        briefing.append("Current: ")
                .append(airportWeather.metar().flightCategory())
                .append(" | ")
                .append(vfrAssessment.message())
                .append(System.lineSeparator());
        briefing.append("Observed UTC: ").append(airportWeather.metar().observationTime()).append(System.lineSeparator());
        briefing.append("Wind: ").append(formatWind(airportWeather.metar().windDir(), airportWeather.metar().windSpeed(), airportWeather.metar().windGust())).append(System.lineSeparator());
        briefing.append("Visibility: ").append(formatOneDecimal(airportWeather.metar().visibilitySm())).append(" SM").append(System.lineSeparator());
        briefing.append("Altimeter: ").append(formatOneDecimal(airportWeather.metar().altimeterInHg())).append(" inHg").append(System.lineSeparator());
        briefing.append("Clouds: ").append(airportWeather.metar().cloudLayersSummary()).append(System.lineSeparator());

        if (airportWeather.airportInfo() != null) {
            briefing.append("Airport: ")
                    .append(airportWeather.airportInfo().municipality())
                    .append(", ")
                    .append(airportWeather.airportInfo().isoRegion())
                    .append(" | Elev ")
                    .append(airportWeather.airportInfo().elevationFt())
                    .append(" ft")
                    .append(System.lineSeparator());
        }

        appendSolarLine(briefing, airportWeather, routePlan, timedRouteAssessment);
        appendTafSummary(briefing, airportWeather.taf());
        appendRunwaySummary(briefing, airportWeather, aircraftProfile);
    }

    private void appendSolarLine(
            StringBuilder briefing,
            AirportWeather airportWeather,
            RoutePlan routePlan,
            TimedRouteAssessment timedRouteAssessment
    ) {
        if (airportWeather.airportInfo() == null) {
            return;
        }

        LocalDateTime relevantTime = null;
        if (routePlan != null && timedRouteAssessment != null) {
            if (airportWeather.metar().airportId().equalsIgnoreCase(routePlan.departureAirport().ident())) {
                relevantTime = timedRouteAssessment.departureTimeUtc();
            } else if (airportWeather.metar().airportId().equalsIgnoreCase(routePlan.destinationAirport().ident())) {
                relevantTime = timedRouteAssessment.arrivalTimeUtc();
            }
        }

        LocalDateTime solarDateSource = relevantTime == null
                ? LocalDateTime.now(ZoneOffset.UTC)
                : relevantTime;
        SolarTimes solarTimes = solarCalculatorService.calculate(airportWeather.airportInfo(), solarDateSource.toLocalDate());
        if (solarTimes == null) {
            return;
        }

        briefing.append("Solar: ").append(formatSolarSummary(solarTimes));
        if (relevantTime != null) {
            briefing.append(" | Planned time is ")
                    .append(solarCalculatorService.isDaylight(solarTimes, relevantTime) ? "daylight" : "night");
        }
        briefing.append(System.lineSeparator());
    }

    private void appendTafSummary(StringBuilder briefing, TafData taf) {
        if (taf == null) {
            briefing.append("TAF: unavailable").append(System.lineSeparator());
            return;
        }

        briefing.append("TAF valid: ").append(taf.validPeriod()).append(System.lineSeparator());
        int periodsToShow = Math.min(3, taf.periods().size());
        for (int index = 0; index < periodsToShow; index++) {
            TafPeriod period = taf.periods().get(index);
            briefing.append("  - ").append(period.label());
            if (period.visibilitySm() != null) {
                briefing.append(" | Vis ").append(formatOneDecimal(period.visibilitySm())).append(" SM");
            }
            if (!period.cloudLayers().isEmpty()) {
                briefing.append(" | ").append(period.cloudLayersSummary());
            }
            briefing.append(System.lineSeparator());
        }
    }

    private void appendRunwaySummary(StringBuilder briefing, AirportWeather airportWeather, AircraftProfile aircraftProfile) {
        List<RunwayAnalysis> runwayAnalyses = runwayAnalysisService.analyze(airportWeather.metar(), airportWeather.runways(), aircraftProfile);
        if (runwayAnalyses.isEmpty()) {
            briefing.append("Runway: unavailable").append(System.lineSeparator());
            return;
        }

        RunwayAnalysis bestRunway = runwayAnalyses.getFirst();
        briefing.append("Best runway: ")
                .append(bestRunway.runway().ident())
                .append(" | HW/TW ").append(formatOneDecimal(bestRunway.components().headwindKts()))
                .append(" | XW ").append(formatOneDecimal(Math.abs(bestRunway.components().crosswindKts())))
                .append(" kt");
        if (bestRunway.exceedsAircraftLimit()) {
            briefing.append(" | Above selected aircraft limit");
        }
        briefing.append(System.lineSeparator());
    }

    private Path defaultBriefingDirectory() {
        return Path.of(System.getProperty("user.home"), ".clouddeck", "briefings");
    }

    private String formatWind(int direction, int speed, int gust) {
        return gust > 0
                ? String.format("%03d deg @ %dG%d kt", direction, speed, gust)
                : String.format("%03d deg @ %d kt", direction, speed);
    }

    private String formatSolarSummary(SolarTimes solarTimes) {
        if (solarTimes.allDaylight()) {
            return "sun above horizon all day";
        }
        if (solarTimes.allNight()) {
            return "sun below horizon all day";
        }
        return "sunrise " + solarTimes.sunriseUtc().toLocalTime() + " UTC, sunset " + solarTimes.sunsetUtc().toLocalTime() + " UTC";
    }

    private String formatDuration(double hours) {
        int wholeHours = (int) hours;
        int minutes = (int) Math.round((hours - wholeHours) * 60);
        if (minutes == 60) {
            wholeHours += 1;
            minutes = 0;
        }
        return wholeHours + "h " + minutes + "m";
    }

    private String formatOneDecimal(double value) {
        return String.format("%.1f", value);
    }
}
