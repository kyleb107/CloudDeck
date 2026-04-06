package com.kylebarnes.clouddeck.ui;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.CrosswindComponents;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.Runway;
import com.kylebarnes.clouddeck.model.SolarTimes;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;
import com.kylebarnes.clouddeck.service.CrosswindCalculator;
import com.kylebarnes.clouddeck.service.DensityAltitudeAssessment;
import com.kylebarnes.clouddeck.service.MetarTrendSummary;
import com.kylebarnes.clouddeck.service.RunwayAnalysis;
import com.kylebarnes.clouddeck.service.VfrAssessment;
import com.kylebarnes.clouddeck.service.VfrStatusLevel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WeatherView {

    private final AppContext ctx;
    private final UiHelper ui;
    private final Consumer<String> openUrl;

    private TextField weatherAirportInput;

    public WeatherView(AppContext ctx, UiHelper ui, Consumer<String> openUrl) {
        this.ctx = ctx;
        this.ui = ui;
        this.openUrl = openUrl;
    }

    public TextField getAirportInput() {
        return weatherAirportInput;
    }

    public ScrollPane build(Stage stage) {
        Label sectionTitle = ui.createSectionTitle("Live Weather");
        Label sectionSubtitle = ui.createSectionSubtitle("Search airports, review TAF outlooks, and compare runway suitability against your selected aircraft.");

        weatherAirportInput = ui.createInputField("Enter ICAO ID(s) ex: KDFW, KHOU, KLFK", 420);
        if (!ctx.appSettings.homeAirport().isBlank()) {
            weatherAirportInput.setText(ctx.appSettings.homeAirport());
        }
        ui.attachAutocomplete(weatherAirportInput, stage);

        Button fetchButton = ui.createPrimaryButton("Load Briefing");
        Button homeButton = ui.createSecondaryButton("Use Home");
        homeButton.setOnAction(event -> {
            if (!ctx.appSettings.homeAirport().isBlank()) {
                weatherAirportInput.setText(ctx.appSettings.homeAirport());
            }
        });
        Label statusLabel = ui.createMutedLabel("");

        fetchButton.setOnAction(event -> {
            String input = weatherAirportInput.getText().trim();
            if (input.isEmpty()) {
                statusLabel.setText("Please enter at least one ICAO airport ID.");
                return;
            }

            ctx.weatherCardsContainer.getChildren().clear();
            statusLabel.setText("Fetching METAR, TAF, and runway data...");

            ctx.runAsync(
                    () -> ctx.weatherService.fetchAirportWeather(input),
                    weather -> {
                        ctx.latestWeatherResults = weather;
                        ctx.resetLazyHistoryStatuses(weather);
                        rerenderWeatherCards();
                        statusLabel.setText("");
                        refreshFavoritesBar(weatherAirportInput);
                    },
                    throwable -> statusLabel.setText("Error: " + throwable.getMessage())
            );
        });

        VBox controlsCard = ui.createPanel(
                "Airport Search",
                "Separate multiple ICAO identifiers with commas.",
                new HBox(12, weatherAirportInput, fetchButton, homeButton)
        );

        VBox favoritesCard = ui.createPanel(
                "Saved Airports",
                "Quick-launch the stations you check most often.",
                ctx.favoritesBar
        );
        refreshFavoritesBar(weatherAirportInput);

        VBox content = new VBox(
                16,
                sectionTitle,
                sectionSubtitle,
                ui.buildSectionFlightStrip(
                        ui.createSectionStripCard("Briefing", "Airport weather deck"),
                        ui.createSectionStripCard("Display", ctx.appSettings.timeDisplayMode().displayName()),
                        ui.createSectionStripCard("Profile", ctx.aircraftSelector.getValue() == null ? "No aircraft" : ctx.aircraftSelector.getValue().name())
                ),
                controlsCard,
                favoritesCard,
                statusLabel,
                ctx.weatherCardsContainer
        );
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }

    public void rerenderWeatherCards() {
        ctx.rerenderWeatherCards = this::rerenderWeatherCards;
        ctx.weatherCardsContainer.getChildren().setAll(
                ctx.latestWeatherResults.stream().map(this::buildAirportCard).collect(Collectors.toList())
        );
    }

    VBox buildAirportCard(AirportWeather airportWeather) {
        AircraftProfile selectedProfile = ctx.aircraftSelector.getValue();
        AirportInfo airportInfo = airportWeather.airportInfo();
        MetarData metar = airportWeather.metar();
        VBox card = new VBox(12);
        card.setPadding(new Insets(20));
        card.setStyle(ui.panelStyle(ctx.themePalette.surfaceBackground(), true));

        String categoryColor = ui.categoryColor(metar.flightCategory());

        Label airportLabel = new Label(metar.airportId());
        airportLabel.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 28px; -fx-font-weight: bold; -fx-letter-spacing: 0.7px;");

        Label categoryBadge = ui.createBadge(metar.flightCategory(), categoryColor);
        Label profileBadge = selectedProfile == null
                ? ui.createBadge("No aircraft selected", ctx.themePalette.unknownGray())
                : ui.createBadge("Aircraft XW limit " + ui.formatSpeed(selectedProfile.maxCrosswindKts()), ctx.themePalette.accentGold());

        Label mastheadLabel = new Label("AIRPORT BRIEFING");
        mastheadLabel.setStyle("-fx-text-fill: " + ctx.themePalette.unknownGray() + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-letter-spacing: 1.1px;");

        HBox badges = new HBox(8, categoryBadge, profileBadge);
        badges.setAlignment(Pos.CENTER_LEFT);

        Button favoriteButton = ui.createGhostButton(ctx.favoritesRepository.isFavorite(metar.airportId()) ? "Saved" : "Save");
        favoriteButton.setOnAction(event -> {
            if (ctx.favoritesRepository.isFavorite(metar.airportId())) {
                ctx.favoritesRepository.removeFavorite(metar.airportId());
                favoriteButton.setText("Save");
            } else {
                ctx.favoritesRepository.addFavorite(metar.airportId());
                favoriteButton.setText("Saved");
            }
            if (weatherAirportInput != null) {
                refreshFavoritesBar(weatherAirportInput);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label nameLabel = ui.makeInfoLabel(metar.airportName());
        nameLabel.setStyle("-fx-text-fill: " + ctx.themePalette.textMuted() + "; -fx-font-size: 15px;");

        VBox titleBlock = new VBox(6, mastheadLabel, airportLabel, nameLabel, badges);
        titleBlock.setMaxWidth(Double.MAX_VALUE);

        VBox rightBlock = new VBox(
                8,
                ui.createSectionStripCard("Observed", ui.formatObservationTime(metar.observationTime())),
                favoriteButton
        );
        rightBlock.setAlignment(Pos.TOP_RIGHT);

        HBox header = new HBox(16, titleBlock, spacer, rightBlock);
        header.setAlignment(Pos.TOP_LEFT);

        VBox stationBanner = ui.createInsetBanner(
                "Current flight category",
                metar.flightCategory() + "  |  " + ui.vfrStatusText(ctx.flightConditionEvaluator.assessVfr(metar, ctx.appSettings).level()),
                categoryColor
        );

        FlowPane metricStrip = new FlowPane();
        metricStrip.setHgap(10);
        metricStrip.setVgap(10);
        metricStrip.getChildren().addAll(
                ui.createMetricCard("Wind", metar.windGust() > 0
                        ? ui.formatWind(metar.windDir(), metar.windSpeed(), metar.windGust())
                        : ui.formatWind(metar.windDir(), metar.windSpeed(), 0), categoryColor),
                ui.createMetricCard("Visibility", ui.formatVisibility(metar.visibilitySm()), ctx.themePalette.accentBlue()),
                ui.createMetricCard("Altimeter", ui.formatAltimeter(metar.altimeterInHg()), ctx.themePalette.accentGold()),
                ui.createMetricCard("Temperature", ui.formatTemperature(metar.tempC()), ctx.themePalette.successGreen())
        );

        VfrAssessment vfrAssessment = ctx.flightConditionEvaluator.assessVfr(metar, ctx.appSettings);
        Label vfrLabel = ui.createStatusLine(vfrAssessment.message(), vfrAssessment.level());

        Label detailsLabel = ui.makeInfoLabel(
                "Clouds: " + metar.cloudLayersSummary() + "  |  Observation: " + ui.formatObservationTime(metar.observationTime())
        );

        VBox airportBriefingSection = buildAirportBriefingSection(airportInfo, metar);
        VBox trendSection = buildTrendSection(airportWeather);
        VBox tafSection = buildTafSection(airportWeather);
        VBox runwaySection = buildRunwaySection(metar, airportWeather.runways(), categoryColor, selectedProfile);

        Label rawLabel = ui.makeInfoLabel("Raw METAR: " + metar.rawObservation());
        rawLabel.setStyle(ui.monospaceMutedStyle());
        rawLabel.setWrapText(true);

        card.getChildren().addAll(header, stationBanner, metricStrip, vfrLabel, detailsLabel, airportBriefingSection, trendSection, tafSection, runwaySection, rawLabel);
        return card;
    }

    VBox buildAirportBriefingSection(AirportInfo airportInfo, MetarData metar) {
        VBox section = new VBox(6);
        section.getChildren().add(new Separator());
        section.getChildren().add(ui.createSubsectionTitle("Airport Briefing"));

        if (airportInfo == null) {
            section.getChildren().add(ui.createMutedLabel("Airport details unavailable for this station."));
            return section;
        }

        String airportType = airportInfo.airportType().replace('_', ' ');
        Label basics = ui.makeInfoLabel(
                airportInfo.municipality() + ", " + airportInfo.isoRegion() + "  |  " +
                        airportType + "  |  Field elev " + airportInfo.elevationFt() + " ft"
        );
        basics.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 12px;");
        section.getChildren().add(basics);

        DensityAltitudeAssessment densityAssessment = ctx.densityAltitudeService.assess(airportInfo, metar, ctx.appSettings);
        if (densityAssessment != null) {
            Label densityLine = ui.createStatusLine(
                    densityAssessment.message()
                            + " Density altitude "
                            + densityAssessment.densityAltitudeFt()
                            + " ft (pressure altitude "
                            + densityAssessment.pressureAltitudeFt()
                            + " ft).",
                    densityAssessment.level()
            );
            section.getChildren().add(densityLine);
        }

        LocalDate briefingDate = ui.extractUtcDate(metar.observationTime());
        SolarTimes solarTimes = ctx.solarCalculatorService.calculate(airportInfo, briefingDate);
        if (solarTimes != null) {
            LocalDateTime observationTimeUtc = ui.extractUtcDateTime(metar.observationTime());
            String solarSummary = ui.formatSolarSummary(solarTimes);
            if (observationTimeUtc != null && observationTimeUtc.toLocalDate().equals(solarTimes.dateUtc())) {
                solarSummary += "  |  Observation " + (ctx.solarCalculatorService.isDaylight(solarTimes, observationTimeUtc)
                        ? "in daylight"
                        : "at night");
            }
            section.getChildren().add(ui.createMutedLabel(solarSummary));
        }

        section.getChildren().add(buildChartResourcesBox(metar.airportId()));

        return section;
    }

    VBox buildChartResourcesBox(String airportId) {
        VBox chartBox = new VBox(6);
        chartBox.getChildren().add(ui.createSubsectionTitle("Chart Resources"));

        chartBox.getChildren().add(buildAirportDiagramPreviewBox(airportId));

        Button diagramButton = ui.createSecondaryButton("Airport Diagram");
        diagramButton.setOnAction(event -> openUrl.accept(ctx.faaChartLinkService.buildAirportDiagramUrl(airportId)));

        Button supplementButton = ui.createSecondaryButton("Chart Supplement");
        supplementButton.setOnAction(event -> openUrl.accept(ctx.faaChartLinkService.buildChartSupplementUrl(airportId)));

        HBox actions = new HBox(10, diagramButton, supplementButton);
        Label hint = ui.createMutedLabel(
                "Opens official FAA chart results in your browser. " + ctx.faaChartLinkService.airportSearchHint(airportId)
        );
        chartBox.getChildren().addAll(actions, hint);
        return chartBox;
    }

    VBox buildTrendSection(AirportWeather airportWeather) {
        VBox section = new VBox(6);
        section.getChildren().add(new Separator());
        section.getChildren().add(ui.createSubsectionTitle("Trend Snapshot"));

        String airportId = ctx.normalizeAirportId(airportWeather.metar().airportId());
        List<MetarData> history = ctx.metarHistoryCache.getOrDefault(airportId, airportWeather.metarHistory());
        String historyStatus = ctx.metarHistoryStatusCache.get(airportId);

        if (history.isEmpty()) {
            if (historyStatus != null) {
                section.getChildren().add(ui.createMutedLabel(historyStatus));
                return section;
            }

            ctx.metarHistoryStatusCache.put(airportId, "Loading recent METAR history...");
            ctx.loadMetarHistoryAsync(airportId);
            section.getChildren().add(ui.createMutedLabel("Loading recent METAR history..."));
            return section;
        }

        MetarTrendSummary trendSummary = ctx.metarTrendService.summarize(history, ctx.appSettings);
        if (trendSummary == null) {
            section.getChildren().add(ui.createMutedLabel("Not enough METAR history yet for a trend view."));
            return section;
        }

        section.getChildren().add(ui.createStatusLine(trendSummary.headline(), trendSummary.level()));
        section.getChildren().add(ui.createMutedLabel(trendSummary.categorySummary()));
        section.getChildren().add(ui.createMutedLabel(trendSummary.visibilitySummary()));
        section.getChildren().add(ui.createMutedLabel(trendSummary.ceilingSummary()));

        VBox recentBox = new VBox(4);
        for (MetarData observation : trendSummary.recentObservations()) {
            Label line = ui.createMutedLabel(
                    ui.formatObservationTime(observation.observationTime())
                            + "  |  " + observation.flightCategory()
                            + "  |  Vis " + ui.formatVisibility(observation.visibilitySm())
            );
            recentBox.getChildren().add(line);
        }
        section.getChildren().add(recentBox);
        return section;
    }

    VBox buildAirportDiagramPreviewBox(String airportId) {
        VBox previewBox = new VBox(6);
        String normalizedAirportId = ctx.normalizeAirportId(airportId);

        Label previewTitle = ui.createMutedLabel("Airport diagram preview");
        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(250);
        imageView.setSmooth(true);

        Label statusLabel = ui.createMutedLabel("Loading FAA airport diagram preview...");

        VBox imageFrame = new VBox(8, imageView, statusLabel);
        imageFrame.setPadding(new Insets(10));
        imageFrame.setStyle(
                "-fx-background-color: " + ctx.themePalette.metricBackground() + "; -fx-border-color: " + ctx.themePalette.metricBorder() + "; " +
                        "-fx-background-radius: 14; -fx-border-radius: 14;"
        );

        Image cachedImage = ctx.airportDiagramImageCache.get(normalizedAirportId);
        if (cachedImage != null) {
            imageView.setImage(cachedImage);
            statusLabel.setText("Click the image to open the full FAA PDF.");
            imageView.setOnMouseClicked(event -> openAirportDiagramPdf(normalizedAirportId));
        } else if (ctx.airportDiagramStatusCache.containsKey(normalizedAirportId)) {
            statusLabel.setText(ctx.airportDiagramStatusCache.get(normalizedAirportId));
        } else {
            if (ctx.airportDiagramLoading.add(normalizedAirportId)) {
                ctx.runAsync(
                        () -> ctx.airportDiagramService.loadPreview(normalizedAirportId),
                        preview -> {
                            ctx.airportDiagramLoading.remove(normalizedAirportId);
                            if (preview == null) {
                                ctx.airportDiagramStatusCache.put(normalizedAirportId, "FAA airport diagram preview unavailable for this airport.");
                            } else {
                                try {
                                    Image image = new Image(preview.imagePath().toUri().toString(), true);
                                    ctx.airportDiagramImageCache.put(normalizedAirportId, image);
                                    ctx.airportDiagramStatusCache.remove(normalizedAirportId);
                                } catch (Exception exception) {
                                    ctx.airportDiagramStatusCache.put(normalizedAirportId, "Diagram loaded, but the preview image could not be displayed.");
                                }
                            }
                            if (ctx.rerenderWeatherCards != null) ctx.rerenderWeatherCards.run();
                            if (ctx.rerenderRouteResults != null) ctx.rerenderRouteResults.run();
                        },
                        throwable -> {
                            ctx.airportDiagramLoading.remove(normalizedAirportId);
                            ctx.airportDiagramStatusCache.put(normalizedAirportId, "Could not load FAA diagram preview: " + throwable.getMessage());
                            if (ctx.rerenderWeatherCards != null) ctx.rerenderWeatherCards.run();
                            if (ctx.rerenderRouteResults != null) ctx.rerenderRouteResults.run();
                        }
                );
            }
        }

        previewBox.getChildren().addAll(previewTitle, imageFrame);
        return previewBox;
    }

    private void openAirportDiagramPdf(String airportId) {
        ctx.runAsync(
                () -> ctx.airportDiagramService.loadPreview(airportId),
                preview -> {
                    if (preview != null) {
                        openUrl.accept(preview.pdfUrl());
                    }
                },
                throwable -> { /* silently ignore */ }
        );
    }

    VBox buildTafSection(AirportWeather airportWeather) {
        VBox section = new VBox(6);
        section.getChildren().add(new Separator());
        section.getChildren().add(ui.createSubsectionTitle("TAF Outlook"));

        TafData taf = airportWeather.taf();
        if (taf == null) {
            String tafStatus = airportWeather.tafStatusMessage();
            section.getChildren().add(ui.createMutedLabel(
                    tafStatus == null || tafStatus.isBlank()
                            ? "TAF unavailable for this airport."
                            : tafStatus
            ));
            return section;
        }

        VfrAssessment tafAssessment = ctx.flightConditionEvaluator.assessTaf(taf, ctx.appSettings);
        section.getChildren().add(ui.createStatusLine(
                tafAssessment == null ? "Forecast available." : tafAssessment.message(),
                tafAssessment == null ? VfrStatusLevel.VFR : tafAssessment.level()
        ));

        Label validityLabel = ui.createMutedLabel("Issued " + taf.issueTime() + "  |  Valid " + taf.validPeriod());
        section.getChildren().add(validityLabel);

        int periodsToShow = Math.min(3, taf.periods().size());
        for (int index = 0; index < periodsToShow; index++) {
            TafPeriod period = taf.periods().get(index);
            VfrAssessment assessment = ui.assessTafPeriod(period);
            Label periodLabel = ui.makeInfoLabel(ui.formatTafPeriod(period, assessment));
            periodLabel.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 12px;");
            periodLabel.setWrapText(true);
            section.getChildren().add(periodLabel);
        }

        if (taf.periods().size() > periodsToShow) {
            section.getChildren().add(ui.createMutedLabel("Additional forecast groups omitted for brevity."));
        }

        Label rawTafLabel = ui.makeInfoLabel("Raw TAF: " + taf.rawText());
        rawTafLabel.setStyle(ui.monospaceMutedStyle());
        rawTafLabel.setWrapText(true);
        section.getChildren().add(rawTafLabel);
        return section;
    }

    VBox buildRunwaySection(MetarData metar, List<Runway> runways, String accentColor, AircraftProfile aircraftProfile) {
        VBox section = new VBox(6);
        section.getChildren().add(new Separator());
        section.getChildren().add(ui.createSubsectionTitle("Runway Suitability"));

        if (runways.isEmpty()) {
            Label noData = ui.createMutedLabel("Runway data not available. Enter a runway heading manually.");
            TextField manualInput = ui.createInputField("ex: 17", 90);
            Label resultLabel = ui.createMutedLabel("");
            Button calculateButton = ui.createSecondaryButton("Check");

            calculateButton.setOnAction(event -> {
                try {
                    int runway = Integer.parseInt(manualInput.getText().trim());
                    if (runway < 1 || runway > 36) {
                        resultLabel.setText("Enter a runway from 1 to 36.");
                        return;
                    }

                    CrosswindComponents components = CrosswindCalculator.calculate(runway * 10, metar.windDir(), metar.windSpeed());
                    String result = ctx.runwayAnalysisService.formatManualResult(components);
                    if (aircraftProfile != null && Math.abs(components.crosswindKts()) > aircraftProfile.maxCrosswindKts()) {
                        result += "  |  Above " + aircraftProfile.name() + " limit";
                    }
                    resultLabel.setText(result);
                } catch (NumberFormatException exception) {
                    resultLabel.setText("Use a numeric runway heading.");
                }
            });

            section.getChildren().addAll(noData, new HBox(10, manualInput, calculateButton), resultLabel);
            return section;
        }

        for (RunwayAnalysis analysis : ctx.runwayAnalysisService.analyze(metar, runways, aircraftProfile)) {
            String headwindLabel = analysis.components().headwindKts() >= 0
                    ? "HW " + ui.formatSpeedValue(analysis.components().headwindKts())
                    : "TW " + ui.formatSpeedValue(Math.abs(analysis.components().headwindKts()));
            String crosswindLabel = "XW " + ui.formatSpeedValue(Math.abs(analysis.components().crosswindKts())) + " "
                    + ui.speedUnitShortLabel() + " "
                    + (analysis.components().crosswindKts() >= 0 ? "R" : "L");

            String text = analysis.bestOption()
                    ? "Best runway " + analysis.runway().ident() + "  |  " + headwindLabel + "  |  " + crosswindLabel
                    : "Runway " + analysis.runway().ident() + "  |  " + headwindLabel + "  |  " + crosswindLabel;

            if (analysis.exceedsAircraftLimit() && aircraftProfile != null) {
                text += "  |  Above " + ui.formatSpeed(aircraftProfile.maxCrosswindKts()) + " limit";
            }

            Label runwayLabel = new Label(text);
            runwayLabel.setWrapText(true);
            runwayLabel.setStyle(analysis.exceedsAircraftLimit()
                    ? "-fx-text-fill: " + ctx.themePalette.warningRed() + "; -fx-font-size: 12px; -fx-font-weight: bold;"
                    : analysis.bestOption()
                    ? "-fx-text-fill: " + accentColor + "; -fx-font-size: 12px; -fx-font-weight: bold;"
                    : "-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 12px;");
            section.getChildren().add(runwayLabel);
        }

        return section;
    }

    public void refreshFavoritesBar(TextField airportInput) {
        ctx.favoritesBar.getChildren().clear();
        List<String> favorites = ctx.favoritesRepository.loadFavorites().stream()
                .filter(favorite -> !favorite.contains("->") && !favorite.contains("\u2192"))
                .toList();

        if (favorites.isEmpty()) {
            ctx.favoritesBar.getChildren().add(ui.createMutedLabel("No saved airports yet. Save one from an airport card."));
            return;
        }

        FlowPane chips = new FlowPane();
        chips.setHgap(8);
        chips.setVgap(8);

        for (String icao : favorites) {
            HBox chip = new HBox(4);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.setStyle(
                    "-fx-background-color: " + ctx.themePalette.metricBackground() + "; -fx-border-color: " + ctx.themePalette.borderColor() + "; " +
                            "-fx-background-radius: 16; -fx-border-radius: 16; -fx-padding: 4px 6px 4px 10px;"
            );

            Button selectButton = new Button(icao);
            selectButton.setStyle("-fx-background-color: transparent; -fx-text-fill: " + ctx.themePalette.accentBlue() + "; -fx-font-size: 12px;");
            selectButton.setOnAction(event -> airportInput.setText(icao));

            Button removeButton = new Button("x");
            removeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: " + ctx.themePalette.favoriteRemoveColor() + "; -fx-font-size: 11px;");
            removeButton.setOnAction(event -> {
                ctx.favoritesRepository.removeFavorite(icao);
                refreshFavoritesBar(airportInput);
            });

            chip.getChildren().addAll(selectButton, removeButton);
            chips.getChildren().add(chip);
        }

        ctx.favoritesBar.getChildren().add(chips);
    }
}
