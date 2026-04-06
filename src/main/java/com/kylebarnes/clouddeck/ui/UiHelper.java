package com.kylebarnes.clouddeck.ui;

import com.kylebarnes.clouddeck.model.AirportSuggestion;
import com.kylebarnes.clouddeck.model.AltimeterUnit;
import com.kylebarnes.clouddeck.model.DistanceUnit;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;
import com.kylebarnes.clouddeck.model.TemperatureUnit;
import com.kylebarnes.clouddeck.model.TimeDisplayMode;
import com.kylebarnes.clouddeck.model.WindUnit;
import com.kylebarnes.clouddeck.service.RouteDecisionLevel;
import com.kylebarnes.clouddeck.service.SolarCalculatorService;
import com.kylebarnes.clouddeck.model.SolarTimes;
import com.kylebarnes.clouddeck.service.VfrAssessment;
import com.kylebarnes.clouddeck.service.VfrStatusLevel;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UiHelper {

    static final String CARD_SHADOW = "dropshadow(gaussian, rgba(0,0,0,0.18), 18, 0.2, 0, 6)";
    static final DateTimeFormatter ROUTE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final DateTimeFormatter CLOCK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    final AppContext ctx;

    UiHelper(AppContext ctx) {
        this.ctx = ctx;
    }

    // -------------------------------------------------------------------------
    // Widget methods
    // -------------------------------------------------------------------------

    Label createSectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 28px; -fx-font-weight: bold; -fx-letter-spacing: 0.5px;");
        return label;
    }

    Label createSectionSubtitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + ctx.themePalette.textMuted() + "; -fx-font-size: 13px; -fx-line-spacing: 1.5px;");
        label.setWrapText(true);
        return label;
    }

    Label createSubsectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 14px; -fx-font-weight: bold;");
        return label;
    }

    VBox createPanel(String title, String subtitle, javafx.scene.Node... content) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 16px; -fx-font-weight: bold; -fx-letter-spacing: 0.4px;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-text-fill: " + ctx.themePalette.textMuted() + "; -fx-font-size: 12px;");
        subtitleLabel.setWrapText(true);

        VBox panelHeader = new VBox(4, titleLabel, subtitleLabel);
        panelHeader.setPadding(new Insets(0, 0, 10, 0));
        panelHeader.setStyle("-fx-border-color: transparent transparent " + ctx.themePalette.metricBorder() + " transparent; -fx-border-width: 0 0 1 0;");

        VBox panel = new VBox(14);
        panel.setPadding(new Insets(18));
        panel.setStyle(panelStyle(ctx.themePalette.surfaceBackgroundAlt(), true));
        panel.getChildren().add(panelHeader);
        panel.getChildren().addAll(content);
        return panel;
    }

    String panelStyle(String background, boolean elevated) {
        return "-fx-background-color: " + background + ";"
                + "-fx-background-radius: 18;"
                + "-fx-border-color: " + ctx.themePalette.borderColor() + ";"
                + "-fx-border-radius: 18;"
                + "-fx-border-width: 1;"
                + (elevated ? "-fx-effect: " + CARD_SHADOW + ";" : "")
                + "-fx-background-insets: 0;";
    }

    VBox createMetricCard(String title, String value, String accentColor) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + ctx.themePalette.unknownGray() + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-letter-spacing: 0.8px;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 16px; -fx-font-weight: bold;");

        VBox card = new VBox(4, titleLabel, valueLabel);
        card.setPadding(new Insets(12));
        card.setMinWidth(130);
        card.setStyle(
                "-fx-background-color: " + ctx.themePalette.metricBackground() + "; -fx-border-color: " + ctx.themePalette.metricBorder() + "; " +
                        "-fx-background-radius: 14; -fx-border-radius: 14; -fx-border-width: 1 1 1 4;"
        );
        return card;
    }

    Label createStatusLine(String text, VfrStatusLevel level) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle(switch (level) {
            case WARNING -> "-fx-text-fill: " + ctx.themePalette.warningRed() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
            case CAUTION -> "-fx-text-fill: " + ctx.themePalette.cautionOrange() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
            case VFR -> "-fx-text-fill: " + ctx.themePalette.successGreen() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
        });
        return label;
    }

    VBox createBanner(String text, RouteDecisionLevel level) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 16px; -fx-font-weight: bold;");

        String background = switch (level) {
            case GO -> ctx.themePalette.bannerGo();
            case CAUTION -> ctx.themePalette.bannerCaution();
            case NO_GO -> ctx.themePalette.bannerNoGo();
        };

        VBox banner = new VBox(label);
        banner.setPadding(new Insets(14));
        banner.setStyle(
                "-fx-background-color: " + background + "; -fx-border-color: " + ctx.themePalette.borderColor() + "; " +
                        "-fx-background-radius: 16; -fx-border-radius: 16;"
        );
        return banner;
    }

    Label createBadge(String text, String color) {
        Label badge = new Label(text);
        badge.setStyle(
                "-fx-background-color: " + ctx.themePalette.badgeBackground() + "; -fx-text-fill: " + color + "; -fx-font-size: 11px; " +
                        "-fx-font-weight: bold; -fx-background-radius: 12; -fx-padding: 4px 10px;"
        );
        return badge;
    }

    String monospaceMutedStyle() {
        return "-fx-text-fill: " + ctx.themePalette.codeText() + "; -fx-font-family: 'Courier New'; -fx-font-size: 11px;";
    }

    Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: linear-gradient(to right, " + ctx.themePalette.primaryGradientStart() + ", " + ctx.themePalette.primaryGradientEnd() + "); -fx-text-fill: white; " +
                        "-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 10px 18px; -fx-background-radius: 14; -fx-cursor: hand; -fx-letter-spacing: 0.8px;"
        );
        return button;
    }

    Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: " + ctx.themePalette.controlBackground() + "; -fx-border-color: " + ctx.themePalette.borderColor() + "; -fx-text-fill: " + ctx.themePalette.textPrimary() + "; " +
                        "-fx-font-size: 12px; -fx-padding: 9px 16px; -fx-background-radius: 14; -fx-border-radius: 14; -fx-cursor: hand; -fx-letter-spacing: 0.5px;"
        );
        return button;
    }

    Button createGhostButton(String text) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: transparent; -fx-border-color: " + ctx.themePalette.borderColor() + "; -fx-text-fill: " + ctx.themePalette.accentGold() + "; " +
                        "-fx-font-size: 12px; -fx-padding: 6px 12px; -fx-background-radius: 14; -fx-border-radius: 14; -fx-cursor: hand; -fx-letter-spacing: 0.5px;"
        );
        return button;
    }

    TextField createInputField(String prompt, double maxWidth) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMaxWidth(maxWidth);
        field.setStyle(
                "-fx-background-color: " + ctx.themePalette.controlBackground() + "; -fx-border-color: " + ctx.themePalette.borderColor() + "; -fx-text-fill: " + ctx.themePalette.textPrimary() + "; " +
                        "-fx-prompt-text-fill: " + ctx.themePalette.unknownGray() + "; -fx-font-size: 13px; -fx-padding: 10px 12px; -fx-background-radius: 14; -fx-border-radius: 14;"
        );
        return field;
    }

    Label createMutedLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + ctx.themePalette.unknownGray() + "; -fx-font-size: 12px;");
        label.setWrapText(true);
        return label;
    }

    Label makeInfoLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.web(ctx.themePalette.textPrimary()));
        label.setFont(Font.font("Bahnschrift SemiCondensed", 13));
        label.setWrapText(true);
        return label;
    }

    Label formLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + ctx.themePalette.textMuted() + "; -fx-font-size: 11px; -fx-font-weight: bold; -fx-letter-spacing: 0.7px;");
        return label;
    }

    HBox buildSectionFlightStrip(VBox... cards) {
        HBox strip = new HBox(12, cards);
        strip.setPadding(new Insets(2, 0, 4, 0));
        return strip;
    }

    VBox createSectionStripCard(String label, String value) {
        Label labelNode = new Label(label.toUpperCase());
        labelNode.setStyle("-fx-text-fill: " + ctx.themePalette.unknownGray() + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-letter-spacing: 1.1px;");

        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        valueNode.setWrapText(true);

        VBox card = new VBox(4, labelNode, valueNode);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setPrefWidth(220);
        card.setStyle(
                "-fx-background-color: " + ctx.themePalette.metricBackground() + "; -fx-border-color: " + ctx.themePalette.metricBorder() + "; " +
                        "-fx-background-radius: 14; -fx-border-radius: 14; -fx-border-width: 1 1 1 4;"
        );
        return card;
    }

    VBox createInsetBanner(String label, String value, String accentColor) {
        Label labelNode = new Label(label.toUpperCase());
        labelNode.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-letter-spacing: 1.2px;");

        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 14px; -fx-font-weight: bold;");
        valueNode.setWrapText(true);

        VBox banner = new VBox(5, labelNode, valueNode);
        banner.setPadding(new Insets(12, 14, 12, 14));
        banner.setStyle(
                "-fx-background-color: " + ctx.themePalette.insetBackground() + "; -fx-border-color: " + accentColor + "; " +
                        "-fx-background-radius: 14; -fx-border-radius: 14; -fx-border-width: 1 1 1 5;"
        );
        return banner;
    }

    VBox createHeaderChip(String label, String value) {
        Label labelNode = new Label(label.toUpperCase());
        labelNode.setStyle("-fx-text-fill: " + ctx.themePalette.accentGold() + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-letter-spacing: 1.0px;");

        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 12px; -fx-font-weight: bold;");

        VBox chip = new VBox(2, labelNode, valueNode);
        chip.setPadding(new Insets(10, 14, 10, 14));
        chip.setStyle(
                "-fx-background-color: " + ctx.themePalette.metricBackground() + "; -fx-border-color: " + ctx.themePalette.metricBorder() + "; " +
                        "-fx-background-radius: 14; -fx-border-radius: 14;"
        );
        return chip;
    }

    HBox aircraftSelectorPlaceholder() {
        Label activeLabel = new Label("Active profile is selected in the header");
        activeLabel.setStyle("-fx-text-fill: " + ctx.themePalette.textMuted() + "; -fx-font-size: 12px;");
        HBox box = new HBox(activeLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    Label createRailEyebrow(String text) {
        Label label = new Label(text.toUpperCase());
        label.setStyle("-fx-text-fill: " + ctx.themePalette.accentGold() + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-letter-spacing: 1.2px;");
        return label;
    }

    Label createRailValue(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 14px; -fx-font-weight: bold;");
        label.setWrapText(true);
        return label;
    }

    // -------------------------------------------------------------------------
    // Format / color methods
    // -------------------------------------------------------------------------

    String vfrStatusText(VfrStatusLevel level) {
        return switch (level) {
            case VFR -> "clear for scan";
            case CAUTION -> "watch item";
            case WARNING -> "attention required";
        };
    }

    String categoryColor(String category) {
        return switch (category) {
            case "VFR" -> ctx.themePalette.successGreen();
            case "MVFR" -> ctx.themePalette.accentBlue();
            case "IFR" -> ctx.themePalette.warningRed();
            case "LIFR" -> "#d789ff";
            default -> ctx.themePalette.unknownGray();
        };
    }

    String statusAccentColor(VfrStatusLevel level) {
        return switch (level) {
            case WARNING -> ctx.themePalette.warningRed();
            case CAUTION -> ctx.themePalette.cautionOrange();
            case VFR -> ctx.themePalette.successGreen();
        };
    }

    String decisionAccentColor(RouteDecisionLevel level) {
        return switch (level) {
            case GO -> ctx.themePalette.successGreen();
            case CAUTION -> ctx.themePalette.cautionOrange();
            case NO_GO -> ctx.themePalette.warningRed();
        };
    }

    VfrAssessment assessTafPeriod(TafPeriod period) {
        TafData syntheticTaf = new TafData("TEMP", "", null, "", period.startTimeUtc(), period.endTimeUtc(), "", List.of(period));
        return ctx.flightConditionEvaluator.assessTaf(syntheticTaf, ctx.appSettings);
    }

    String formatTafPeriod(TafPeriod period, VfrAssessment assessment) {
        StringBuilder builder = new StringBuilder();
        builder.append(period.label()).append(": ").append(assessment == null ? "Forecast available" : assessment.level());

        if (period.visibilitySm() != null) {
            builder.append(" | Vis ").append(formatVisibility(period.visibilitySm()));
        }
        if (!period.cloudLayers().isEmpty()) {
            builder.append(" | ").append(period.cloudLayersSummary());
        }
        if (!period.weatherTokens().isEmpty()) {
            builder.append(" | Wx ").append(String.join(" ", period.weatherTokens()));
        }

        return builder.toString();
    }

    String formatDuration(double hours) {
        int wholeHours = (int) hours;
        int minutes = (int) Math.round((hours - wholeHours) * 60);
        if (minutes == 60) {
            wholeHours += 1;
            minutes = 0;
        }
        return wholeHours + "h " + minutes + "m";
    }

    String formatOneDecimal(double value) {
        return String.format("%.1f", value);
    }

    String formatTemperature(float tempC) {
        if (ctx.appSettings.temperatureUnit() == TemperatureUnit.CELSIUS) {
            return formatOneDecimal(tempC) + " C";
        }
        return formatOneDecimal((tempC * 9 / 5) + 32) + " F";
    }

    String formatVisibility(float visibilitySm) {
        return formatOneDecimal(visibilitySm) + " SM";
    }

    String formatDistance(double distanceNm) {
        if (ctx.appSettings.distanceUnit() == DistanceUnit.STATUTE_MILES) {
            return formatOneDecimal(distanceNm * 1.15078) + " mi";
        }
        return formatOneDecimal(distanceNm) + " nm";
    }

    String formatSpeed(double speedKts) {
        return formatSpeedValue(speedKts) + " " + speedUnitShortLabel();
    }

    String formatSignedSpeed(double speedKts) {
        return (speedKts >= 0 ? "+" : "-") + formatSpeed(Math.abs(speedKts));
    }

    String formatSpeedValue(double speedKts) {
        double convertedSpeed = switch (ctx.appSettings.windUnit()) {
            case KNOTS -> speedKts;
            case MILES_PER_HOUR -> speedKts * 1.15078;
            case KILOMETERS_PER_HOUR -> speedKts * 1.852;
        };
        return formatOneDecimal(convertedSpeed);
    }

    String speedUnitShortLabel() {
        return switch (ctx.appSettings.windUnit()) {
            case KNOTS -> "kt";
            case MILES_PER_HOUR -> "mph";
            case KILOMETERS_PER_HOUR -> "km/h";
        };
    }

    String formatWind(int direction, int speedKts, int gustKts) {
        return gustKts > 0
                ? String.format("%03d deg @ %sG%s %s", direction, formatSpeedValue(speedKts), formatSpeedValue(gustKts), speedUnitShortLabel())
                : String.format("%03d deg @ %s %s", direction, formatSpeedValue(speedKts), speedUnitShortLabel());
    }

    String formatCompactWind(int direction, int speedKts, int gustKts) {
        return gustKts > 0
                ? String.format("%03d/%sG%s %s", direction, formatSpeedValue(speedKts), formatSpeedValue(gustKts), speedUnitShortLabel())
                : String.format("%03d/%s %s", direction, formatSpeedValue(speedKts), speedUnitShortLabel());
    }

    String formatAltimeter(float altimeterInHg) {
        if (ctx.appSettings.altimeterUnit() == AltimeterUnit.HPA) {
            return Math.round(altimeterInHg * 33.8639f) + " hPa";
        }
        return String.format("%.2f inHg", altimeterInHg);
    }

    String formatDateTime(LocalDateTime timeUtc) {
        if (timeUtc == null) {
            return "";
        }
        if (ctx.appSettings.timeDisplayMode() == TimeDisplayMode.UTC) {
            return timeUtc.format(ROUTE_TIME_FORMATTER) + " UTC";
        }
        return formatLocalDateTime(timeUtc);
    }

    String formatClockTime(LocalDateTime timeUtc) {
        if (timeUtc == null) {
            return "";
        }
        if (ctx.appSettings.timeDisplayMode() == TimeDisplayMode.UTC) {
            return timeUtc.toLocalTime().format(CLOCK_FORMATTER) + " UTC";
        }
        ZonedDateTime localTime = toLocalZonedDateTime(timeUtc);
        return localTime.format(DateTimeFormatter.ofPattern("HH:mm z"));
    }

    String formatUtcDateTime(LocalDateTime timeUtc) {
        return timeUtc == null ? "" : timeUtc.format(ROUTE_TIME_FORMATTER) + " UTC";
    }

    String formatLocalDateTime(LocalDateTime timeUtc) {
        if (timeUtc == null) {
            return "";
        }
        return toLocalZonedDateTime(timeUtc).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));
    }

    ZonedDateTime toLocalZonedDateTime(LocalDateTime timeUtc) {
        return timeUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault());
    }

    String formatObservationTime(String observationTime) {
        LocalDateTime observationTimeUtc = extractUtcDateTime(observationTime);
        if (observationTimeUtc == null) {
            return observationTime == null ? "" : observationTime.replace("T", " ").replace(".000Z", "Z");
        }
        return formatDateTime(observationTimeUtc);
    }

    String timeDisplayShortLabel() {
        return ctx.appSettings.timeDisplayMode() == TimeDisplayMode.UTC ? "UTC" : "Local";
    }

    String formatSolarSummary(SolarTimes solarTimes) {
        if (solarTimes.allDaylight()) {
            return "Sun above horizon all day on " + solarTimes.dateUtc() + " " + timeDisplayShortLabel();
        }
        if (solarTimes.allNight()) {
            return "Sun below horizon all day on " + solarTimes.dateUtc() + " " + timeDisplayShortLabel();
        }
        return "Sunrise " + formatClockTime(solarTimes.sunriseUtc())
                + "  |  Sunset " + formatClockTime(solarTimes.sunsetUtc());
    }

    String formatSolarPlanningLine(String airportId, String phase, SolarTimes solarTimes, LocalDateTime timeUtc) {
        SolarCalculatorService solarCalculatorService = ctx.solarCalculatorService;
        String condition = solarCalculatorService.isDaylight(solarTimes, timeUtc) ? "daylight" : "night";
        return airportId + " " + phase + " at " + formatClockTime(timeUtc)
                + " is in " + condition + "  |  " + formatSolarSummary(solarTimes);
    }

    LocalDateTime extractUtcDateTime(String observationTime) {
        if (observationTime == null || observationTime.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(observationTime).atOffset(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    LocalDate extractUtcDate(String observationTime) {
        LocalDateTime observationTimeUtc = extractUtcDateTime(observationTime);
        if (observationTimeUtc != null) {
            return observationTimeUtc.toLocalDate();
        }
        return LocalDate.now(ZoneOffset.UTC);
    }

    // -------------------------------------------------------------------------
    // Autocomplete helpers
    // -------------------------------------------------------------------------

    void attachAutocomplete(TextField input, Stage stage) {
        ListView<String> suggestionList = new ListView<>();
        suggestionList.setStyle(
                "-fx-background-color: " + ctx.themePalette.listBackground() + "; -fx-border-color: " + ctx.themePalette.borderColor() + "; -fx-border-width: 1px;"
        );
        suggestionList.setPrefHeight(140);
        suggestionList.setMaxWidth(420);

        Popup popup = new Popup();
        popup.getContent().add(suggestionList);
        popup.setAutoHide(true);

        input.textProperty().addListener((observable, oldValue, newValue) -> {
            String[] parts = newValue.split(",");
            String currentQuery = parts[parts.length - 1].trim();

            if (currentQuery.length() < 2) {
                popup.hide();
                return;
            }

            ctx.runAsync(
                    () -> ctx.airportsRepository.suggestAirports(currentQuery, 6),
                    suggestions -> showSuggestions(input, stage, popup, suggestionList, suggestions),
                    throwable -> popup.hide()
            );
        });

        suggestionList.setOnMouseClicked(event -> {
            String selected = suggestionList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }

            String icao = selected.split("  -  ")[0].trim();
            String[] parts = input.getText().split(",");
            parts[parts.length - 1] = " " + icao;
            input.setText(String.join(",", parts).trim());
            input.positionCaret(input.getText().length());
            popup.hide();
        });
    }

    void showSuggestions(TextField input, Stage stage, Popup popup, ListView<String> suggestionList, List<AirportSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            popup.hide();
            return;
        }

        suggestionList.getItems().setAll(suggestions.stream().map(AirportSuggestion::displayLabel).toList());
        Bounds bounds = input.localToScreen(input.getBoundsInLocal());
        if (bounds != null) {
            popup.show(stage, bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }
}
