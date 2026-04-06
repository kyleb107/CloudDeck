package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AltimeterUnit;
import com.kylebarnes.clouddeck.model.AlternateAirportOption;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.RoutePlan;
import com.kylebarnes.clouddeck.model.SolarTimes;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;
import com.kylebarnes.clouddeck.model.TimeDisplayMode;
import com.kylebarnes.clouddeck.model.TimedRouteAssessment;
import com.kylebarnes.clouddeck.model.WindUnit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BriefingExportService {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final float PAGE_MARGIN = 50f;
    private static final float BODY_FONT_SIZE = 11f;
    private static final float HEADING_FONT_SIZE = 14f;
    private static final float TITLE_FONT_SIZE = 20f;

    private final FlightConditionEvaluator flightConditionEvaluator = new FlightConditionEvaluator();
    private final RunwayAnalysisService runwayAnalysisService = new RunwayAnalysisService();
    private final SolarCalculatorService solarCalculatorService = new SolarCalculatorService();

    public BriefingExportResult exportRouteBriefing(
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
        String baseFilename = "clouddeck-briefing-" + departureId + "-" + destinationId + "-" + LocalDateTime.now(ZoneOffset.UTC).format(FILE_STAMP);
        Path textPath = defaultBriefingDirectory().resolve(baseFilename + ".txt");
        Path pdfPath = defaultBriefingDirectory().resolve(baseFilename + ".pdf");
        String briefingText = buildRouteBriefing(
                routePlan,
                aircraftProfile,
                routeWeather,
                routeAssessment,
                timedRouteAssessment,
                alerts,
                alternates,
                appSettings
        );
        Files.writeString(
                textPath,
                briefingText,
                StandardCharsets.UTF_8
        );
        exportPdfBriefing(pdfPath, briefingText);
        return new BriefingExportResult(textPath, pdfPath);
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
        String routeLabel = routePlan == null
                ? "Route briefing"
                : routePlan.departureAirport().ident() + " -> " + routePlan.destinationAirport().ident();

        briefing.append("CloudDeck Flight Briefing").append(System.lineSeparator());
        briefing.append(routeLabel).append(System.lineSeparator());
        briefing.append("Generated: ").append(formatDateTime(generatedAtUtc, appSettings)).append(System.lineSeparator());
        briefing.append(repeat("=", 60)).append(System.lineSeparator()).append(System.lineSeparator());

        appendSectionHeading(briefing, "Route Decision");
        if (timedRouteAssessment != null) {
            appendKeyValue(briefing, "Timed outlook", timedRouteAssessment.level() + " - " + timedRouteAssessment.message());
            appendKeyValue(briefing, "Departure", formatDateTime(timedRouteAssessment.departureTimeUtc(), appSettings));
            appendKeyValue(briefing, "Arrival", formatDateTime(timedRouteAssessment.arrivalTimeUtc(), appSettings));
        } else if (routeAssessment != null) {
            appendKeyValue(briefing, "Route outlook", routeAssessment.level() + " - " + routeAssessment.message());
        } else {
            appendKeyValue(briefing, "Route outlook", "Unavailable");
        }
        briefing.append(System.lineSeparator());

        appendSectionHeading(briefing, "Aircraft and Planning");
        if (aircraftProfile == null) {
            appendBullet(briefing, "No aircraft profile selected. Fuel and runway-limit planning are omitted.");
        } else {
            appendBullet(briefing,
                    aircraftProfile.name()
                            + " | Cruise " + formatSpeed(aircraftProfile.cruiseSpeedKts(), appSettings)
                            + " | Burn " + formatOneDecimal(aircraftProfile.fuelBurnGph()) + " gph"
                            + " | Reserve " + formatOneDecimal(aircraftProfile.reserveFuelGallons()) + " gal");
        }
        if (routePlan != null) {
            appendKeyValue(briefing, "Distance", formatDistance(routePlan.distanceNm(), appSettings));
            appendKeyValue(briefing, "Groundspeed", formatSpeed(routePlan.groundspeedKts(), appSettings));
            appendKeyValue(briefing, "ETE", formatDuration(routePlan.estimatedTimeHours()));
            appendKeyValue(briefing, "Airborne fuel", formatOneDecimal(routePlan.airborneFuelGallons()) + " gal");
            appendKeyValue(briefing, "Taxi fuel", formatOneDecimal(routePlan.taxiFuelGallons()) + " gal");
            appendKeyValue(briefing, "Climb fuel", formatOneDecimal(routePlan.climbFuelGallons()) + " gal");
            appendKeyValue(briefing, "Trip fuel", formatOneDecimal(routePlan.tripFuelGallons()) + " gal");
            appendKeyValue(briefing, "Reserve remaining", formatOneDecimal(routePlan.reserveRemainingGallons()) + " gal");
        }
        appendKeyValue(briefing, "Theme preset", appSettings.themePreset().displayName());
        briefing.append(System.lineSeparator());

        appendSectionHeading(briefing, "Operational Alerts");
        if (alerts == null || alerts.isEmpty()) {
            appendBullet(briefing, "No additional operational alerts.");
        } else {
            for (OperationalAlert alert : alerts) {
                appendBullet(briefing, alert.level() + ": " + alert.title() + " - " + alert.detail());
            }
        }
        briefing.append(System.lineSeparator());

        appendSectionHeading(briefing, "Airport Details");
        for (AirportWeather airportWeather : routeWeather) {
            appendAirportSection(briefing, airportWeather, aircraftProfile, appSettings, routePlan, timedRouteAssessment);
        }

        appendSectionHeading(briefing, "Alternates");
        if (alternates == null || alternates.isEmpty()) {
            appendBullet(briefing, "No alternate suggestions included.");
        } else {
            for (AlternateAirportOption alternate : alternates) {
                appendBullet(
                        briefing,
                        alternate.airportWeather().metar().airportId()
                                + " | "
                                + formatDistance(alternate.distanceFromDestinationNm(), appSettings)
                                + " from destination | "
                                + alternate.summary()
                );
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
        briefing.append(repeat("-", 40)).append(System.lineSeparator());

        VfrAssessment vfrAssessment = flightConditionEvaluator.assessVfr(airportWeather.metar(), appSettings);
        appendKeyValue(
                briefing,
                "Current",
                airportWeather.metar().flightCategory() + " | " + vfrAssessment.message()
        );
        appendKeyValue(briefing, "Observed", formatObservationTime(airportWeather.metar().observationTime(), appSettings));
        appendKeyValue(briefing, "Wind", formatWind(airportWeather.metar().windDir(), airportWeather.metar().windSpeed(), airportWeather.metar().windGust(), appSettings));
        appendKeyValue(briefing, "Visibility", formatVisibility(airportWeather.metar().visibilitySm()));
        appendKeyValue(briefing, "Altimeter", formatAltimeter(airportWeather.metar().altimeterInHg(), appSettings));
        appendKeyValue(briefing, "Clouds", airportWeather.metar().cloudLayersSummary());

        if (airportWeather.airportInfo() != null) {
            appendKeyValue(
                    briefing,
                    "Airport",
                    airportWeather.airportInfo().municipality()
                            + ", "
                            + airportWeather.airportInfo().isoRegion()
                            + " | Elev "
                            + airportWeather.airportInfo().elevationFt()
                            + " ft"
            );
        }

        appendSolarLine(briefing, airportWeather, routePlan, timedRouteAssessment, appSettings);
        appendTafSummary(briefing, airportWeather.taf());
        if (airportWeather.taf() == null && airportWeather.tafStatusMessage() != null) {
            appendKeyValue(briefing, "TAF status", airportWeather.tafStatusMessage());
        }
        appendRunwaySummary(briefing, airportWeather, aircraftProfile, appSettings);
    }

    private void appendSolarLine(
            StringBuilder briefing,
            AirportWeather airportWeather,
            RoutePlan routePlan,
            TimedRouteAssessment timedRouteAssessment,
            AppSettings appSettings
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

        appendKeyValue(briefing, "Solar", formatSolarSummary(solarTimes, appSettings));
        if (relevantTime != null) {
            briefing.append("  Planned time is ")
                    .append(solarCalculatorService.isDaylight(solarTimes, relevantTime) ? "daylight" : "night");
        }
        briefing.append(System.lineSeparator());
    }

    private void appendTafSummary(StringBuilder briefing, TafData taf) {
        if (taf == null) {
            appendKeyValue(briefing, "TAF", "Unavailable");
            return;
        }

        appendKeyValue(briefing, "TAF valid", taf.validPeriod());
        int periodsToShow = Math.min(3, taf.periods().size());
        for (int index = 0; index < periodsToShow; index++) {
            TafPeriod period = taf.periods().get(index);
            briefing.append("  - ").append(period.label());
            if (period.visibilitySm() != null) {
                briefing.append(" | Vis ").append(formatVisibility(period.visibilitySm()));
            }
            if (!period.cloudLayers().isEmpty()) {
                briefing.append(" | ").append(period.cloudLayersSummary());
            }
            briefing.append(System.lineSeparator());
        }
    }

    private void appendRunwaySummary(StringBuilder briefing, AirportWeather airportWeather, AircraftProfile aircraftProfile, AppSettings appSettings) {
        List<RunwayAnalysis> runwayAnalyses = runwayAnalysisService.analyze(airportWeather.metar(), airportWeather.runways(), aircraftProfile);
        if (runwayAnalyses.isEmpty()) {
            appendKeyValue(briefing, "Runway", "Unavailable");
            return;
        }

        RunwayAnalysis bestRunway = runwayAnalyses.getFirst();
        briefing.append("Best runway: ")
                .append(bestRunway.runway().ident())
                .append(" | HW/TW ").append(formatSpeed(bestRunway.components().headwindKts(), appSettings))
                .append(" | XW ").append(formatSpeed(Math.abs(bestRunway.components().crosswindKts()), appSettings));
        if (bestRunway.exceedsAircraftLimit()) {
            briefing.append(" | Above selected aircraft limit");
        }
        briefing.append(System.lineSeparator());
    }

    private Path defaultBriefingDirectory() {
        return Path.of(System.getProperty("user.home"), ".clouddeck", "briefings");
    }

    private void exportPdfBriefing(Path pdfPath, String briefingText) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont headingFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            List<String> lines = List.of(briefingText.split("\\R", -1));
            List<LineToken> tokens = tokenize(lines);

            PDPage page = null;
            PDPageContentStream contentStream = null;
            float y = 0;
            try {
                for (LineToken token : tokens) {
                    PDFont font = switch (token.style()) {
                        case TITLE -> titleFont;
                        case HEADING -> headingFont;
                        case BODY -> bodyFont;
                    };
                    float fontSize = switch (token.style()) {
                        case TITLE -> TITLE_FONT_SIZE;
                        case HEADING -> HEADING_FONT_SIZE;
                        case BODY -> BODY_FONT_SIZE;
                    };
                    float leading = switch (token.style()) {
                        case TITLE -> 26f;
                        case HEADING -> 20f;
                        case BODY -> 15f;
                    };

                    List<String> wrappedLines = wrapLine(token.text(), font, fontSize, PDRectangle.LETTER.getWidth() - (PAGE_MARGIN * 2));
                    if (wrappedLines.isEmpty()) {
                        if (contentStream == null) {
                            page = new PDPage(PDRectangle.LETTER);
                            document.addPage(page);
                            contentStream = new PDPageContentStream(document, page);
                            y = page.getMediaBox().getHeight() - PAGE_MARGIN;
                        }
                        y -= leading * 0.7f;
                        continue;
                    }

                    for (String line : wrappedLines) {
                        if (contentStream == null || y < PAGE_MARGIN + leading) {
                            if (contentStream != null) {
                                contentStream.close();
                            }
                            page = new PDPage(PDRectangle.LETTER);
                            document.addPage(page);
                            contentStream = new PDPageContentStream(document, page);
                            y = page.getMediaBox().getHeight() - PAGE_MARGIN;
                        }

                        contentStream.beginText();
                        contentStream.setFont(font, fontSize);
                        contentStream.newLineAtOffset(PAGE_MARGIN, y);
                        contentStream.showText(line);
                        contentStream.endText();
                        y -= leading;
                    }
                }
            } finally {
                if (contentStream != null) {
                    contentStream.close();
                }
            }

            document.save(pdfPath.toFile());
        }
    }

    private List<LineToken> tokenize(List<String> lines) {
        List<LineToken> tokens = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (index == 0) {
                tokens.add(new LineToken(line, LineStyle.TITLE));
            } else if (line.matches("^[A-Z][A-Za-z ]+$") && index + 1 < lines.size() && lines.get(index + 1).startsWith("---")) {
                tokens.add(new LineToken(line, LineStyle.HEADING));
            } else if (!line.startsWith("---") && !line.startsWith("===")) {
                tokens.add(new LineToken(line, LineStyle.BODY));
            }
        }
        return tokens;
    }

    private List<String> wrapLine(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            float width = font.getStringWidth(candidate) / 1000 * fontSize;
            if (width > maxWidth && !currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
                currentLine.append(word);
            } else {
                currentLine.setLength(0);
                currentLine.append(candidate);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private void appendSectionHeading(StringBuilder briefing, String title) {
        briefing.append(title).append(System.lineSeparator());
        briefing.append(repeat("-", title.length())).append(System.lineSeparator());
    }

    private void appendKeyValue(StringBuilder briefing, String label, String value) {
        briefing.append(label).append(": ").append(value).append(System.lineSeparator());
    }

    private void appendBullet(StringBuilder briefing, String text) {
        briefing.append("- ").append(text).append(System.lineSeparator());
    }

    private String formatWind(int direction, int speed, int gust, AppSettings appSettings) {
        return gust > 0
                ? String.format("%03d deg @ %sG%s %s", direction, formatSpeedValue(speed, appSettings), formatSpeedValue(gust, appSettings), speedUnitLabel(appSettings))
                : String.format("%03d deg @ %s %s", direction, formatSpeedValue(speed, appSettings), speedUnitLabel(appSettings));
    }

    private String formatSolarSummary(SolarTimes solarTimes, AppSettings appSettings) {
        if (solarTimes.allDaylight()) {
            return "sun above horizon all day";
        }
        if (solarTimes.allNight()) {
            return "sun below horizon all day";
        }
        return "sunrise " + formatDateTime(solarTimes.sunriseUtc(), appSettings) + ", sunset " + formatDateTime(solarTimes.sunsetUtc(), appSettings);
    }

    private String formatVisibility(float visibilitySm) {
        return formatOneDecimal(visibilitySm) + " SM";
    }

    private String formatAltimeter(float altimeterInHg, AppSettings appSettings) {
        if (appSettings.altimeterUnit() == AltimeterUnit.HPA) {
            return Math.round(altimeterInHg * 33.8639f) + " hPa";
        }
        return String.format(Locale.US, "%.2f inHg", altimeterInHg);
    }

    private String formatDistance(double distanceNm, AppSettings appSettings) {
        if (appSettings.distanceUnit() == com.kylebarnes.clouddeck.model.DistanceUnit.STATUTE_MILES) {
            return formatOneDecimal(distanceNm * 1.15078) + " mi";
        }
        return formatOneDecimal(distanceNm) + " nm";
    }

    private String formatSpeed(double speedKts, AppSettings appSettings) {
        return formatSpeedValue(speedKts, appSettings) + " " + speedUnitLabel(appSettings);
    }

    private String formatSpeedValue(double speedKts, AppSettings appSettings) {
        double convertedSpeed = switch (appSettings.windUnit()) {
            case KNOTS -> speedKts;
            case MILES_PER_HOUR -> speedKts * 1.15078;
            case KILOMETERS_PER_HOUR -> speedKts * 1.852;
        };
        return formatOneDecimal(convertedSpeed);
    }

    private String speedUnitLabel(AppSettings appSettings) {
        return switch (appSettings.windUnit()) {
            case KNOTS -> "kt";
            case MILES_PER_HOUR -> "mph";
            case KILOMETERS_PER_HOUR -> "km/h";
        };
    }

    private String formatObservationTime(String observationTime, AppSettings appSettings) {
        try {
            LocalDateTime utc = java.time.Instant.parse(observationTime).atOffset(ZoneOffset.UTC).toLocalDateTime();
            return formatDateTime(utc, appSettings);
        } catch (Exception exception) {
            return observationTime;
        }
    }

    private String formatDateTime(LocalDateTime utcTime, AppSettings appSettings) {
        if (appSettings.timeDisplayMode() == TimeDisplayMode.UTC) {
            return utcTime.format(TIME_FORMATTER) + " UTC";
        }

        ZonedDateTime localTime = utcTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault());
        return localTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));
    }

    private String repeat(String text, int count) {
        return text.repeat(Math.max(count, 0));
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
        return String.format(Locale.US, "%.1f", value);
    }

    private enum LineStyle {
        TITLE,
        HEADING,
        BODY
    }

    private record LineToken(String text, LineStyle style) {
    }
}
