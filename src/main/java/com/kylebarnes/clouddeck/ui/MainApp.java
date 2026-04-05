package com.kylebarnes.clouddeck.ui;

import com.kylebarnes.clouddeck.data.AviationWeatherClient;
import com.kylebarnes.clouddeck.data.MetarParser;
import com.kylebarnes.clouddeck.data.OurAirportsRepository;
import com.kylebarnes.clouddeck.data.TafClient;
import com.kylebarnes.clouddeck.data.TafParser;
import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.AirportSuggestion;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.CrosswindComponents;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.RoutePlan;
import com.kylebarnes.clouddeck.model.Runway;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;
import com.kylebarnes.clouddeck.service.CrosswindCalculator;
import com.kylebarnes.clouddeck.service.FlightConditionEvaluator;
import com.kylebarnes.clouddeck.service.FlightPlanningService;
import com.kylebarnes.clouddeck.service.RouteAssessment;
import com.kylebarnes.clouddeck.service.RouteDecisionLevel;
import com.kylebarnes.clouddeck.service.RunwayAnalysis;
import com.kylebarnes.clouddeck.service.RunwayAnalysisService;
import com.kylebarnes.clouddeck.service.VfrAssessment;
import com.kylebarnes.clouddeck.service.VfrStatusLevel;
import com.kylebarnes.clouddeck.service.WeatherService;
import com.kylebarnes.clouddeck.storage.AircraftProfileRepository;
import com.kylebarnes.clouddeck.storage.FavoritesRepository;
import com.kylebarnes.clouddeck.storage.LocalAircraftProfileRepository;
import com.kylebarnes.clouddeck.storage.LocalFavoritesRepository;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MainApp extends Application {
    private static final String APP_BACKGROUND = "#0e1623";
    private static final String APP_BACKGROUND_ALT = "#122033";
    private static final String SURFACE_BACKGROUND = "#172537";
    private static final String SURFACE_BACKGROUND_ALT = "#1d3048";
    private static final String BORDER_COLOR = "#27425f";
    private static final String ACCENT_BLUE = "#5ec2ff";
    private static final String ACCENT_GOLD = "#ffd166";
    private static final String SUCCESS_GREEN = "#5ee18b";
    private static final String WARNING_RED = "#ff6b6b";
    private static final String CAUTION_ORANGE = "#ffb454";
    private static final String UNKNOWN_GRAY = "#90a4b7";
    private static final String TEXT_PRIMARY = "#eaf3ff";
    private static final String TEXT_MUTED = "#bfd0e0";
    private static final String CARD_SHADOW = "dropshadow(gaussian, rgba(0,0,0,0.18), 18, 0.2, 0, 6)";

    private final OurAirportsRepository airportsRepository = new OurAirportsRepository();
    private final WeatherService weatherService = new WeatherService(
            new AviationWeatherClient(),
            new TafClient(),
            new MetarParser(),
            new TafParser(),
            airportsRepository
    );
    private final FlightConditionEvaluator flightConditionEvaluator = new FlightConditionEvaluator();
    private final RunwayAnalysisService runwayAnalysisService = new RunwayAnalysisService();
    private final FlightPlanningService flightPlanningService = new FlightPlanningService();
    private final FavoritesRepository favoritesRepository = new LocalFavoritesRepository();
    private final AircraftProfileRepository aircraftProfileRepository = new LocalAircraftProfileRepository();
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("clouddeck-background");
        return thread;
    });

    private final VBox favoritesBar = new VBox(6);
    private final VBox weatherCardsContainer = new VBox(14);
    private final VBox routeResultsBox = new VBox(12);
    private final VBox aircraftSummaryBox = new VBox(8);
    private final ComboBox<AircraftProfile> aircraftSelector = new ComboBox<>();
    private final Label aircraftHeroSummary = new Label();
    private final Label aircraftHeroNote = new Label();

    private TextField weatherAirportInput;
    private Label routeStatusLabel;
    private List<AirportWeather> latestWeatherResults = List.of();
    private List<AirportWeather> latestRouteResults = List.of();
    private String latestRouteDeparture;
    private String latestRouteDestination;

    @Override
    public void start(Stage stage) {
        reloadAircraftProfiles(null);
        aircraftSelector.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateAircraftDisplays();
            rerenderWeatherCards();
            rerenderRouteResults();
        });

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, " + APP_BACKGROUND + ", " + APP_BACKGROUND_ALT + ");");
        root.setTop(buildAppHeader());

        TabPane tabPane = new TabPane(
                new Tab("Weather", buildWeatherTab(stage)),
                new Tab("Route Planner", buildRouteTab(stage)),
                new Tab("Aircraft", buildAircraftTab())
        );
        tabPane.getTabs().forEach(tab -> tab.setClosable(false));
        tabPane.setStyle("-fx-background-color: transparent;");
        root.setCenter(tabPane);

        Scene scene = new Scene(root, 1100, 820);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        stage.setTitle("CloudDeck");
        stage.setScene(scene);
        stage.show();

        updateAircraftDisplays();
    }

    @Override
    public void stop() {
        backgroundExecutor.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private VBox buildAppHeader() {
        VBox header = new VBox(18);
        header.setPadding(new Insets(26, 28, 18, 28));
        header.setStyle("-fx-background-color: rgba(7, 13, 22, 0.24);");

        Label eyebrow = new Label("Pilot briefing workspace");
        eyebrow.setStyle("-fx-text-fill: " + ACCENT_GOLD + "; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label title = new Label("CloudDeck");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 38));
        title.setTextFill(Color.web(TEXT_PRIMARY));

        Label subtitle = new Label("Weather, forecasts, runway suitability, and fuel planning in one cockpit-friendly workflow.");
        subtitle.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 14px;");
        subtitle.setWrapText(true);

        VBox branding = new VBox(8, eyebrow, title, subtitle);
        branding.setMaxWidth(540);

        Label selectorLabel = new Label("Selected Aircraft");
        selectorLabel.setStyle("-fx-text-fill: " + UNKNOWN_GRAY + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        aircraftSelector.setPromptText("Choose an aircraft profile");
        aircraftSelector.setPrefWidth(320);
        aircraftSelector.setStyle(
                "-fx-background-color: rgba(255,255,255,0.08); -fx-border-color: " + BORDER_COLOR + "; " +
                        "-fx-text-fill: white; -fx-background-radius: 12; -fx-border-radius: 12; -fx-padding: 4px;"
        );

        aircraftHeroSummary.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        aircraftHeroNote.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 12px;");
        aircraftHeroNote.setWrapText(true);

        VBox aircraftCard = createPanel(
                "Aircraft Profile",
                "Runway warnings and route fuel calculations use the active profile.",
                new VBox(10, selectorLabel, aircraftSelector, aircraftHeroSummary, aircraftHeroNote)
        );
        aircraftCard.setMaxWidth(380);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topRow = new HBox(24, branding, spacer, aircraftCard);
        topRow.setAlignment(Pos.TOP_LEFT);
        header.getChildren().add(topRow);
        return header;
    }
    private ScrollPane buildWeatherTab(Stage stage) {
        Label sectionTitle = createSectionTitle("Live Weather");
        Label sectionSubtitle = createSectionSubtitle("Search airports, review TAF outlooks, and compare runway suitability against your selected aircraft.");

        weatherAirportInput = createInputField("Enter ICAO ID(s) ex: KDFW, KHOU, KLFK", 420);
        attachAutocomplete(weatherAirportInput, stage);

        Button fetchButton = createPrimaryButton("Load Briefing");
        Label statusLabel = createMutedLabel("");

        fetchButton.setOnAction(event -> {
            String input = weatherAirportInput.getText().trim();
            if (input.isEmpty()) {
                statusLabel.setText("Please enter at least one ICAO airport ID.");
                return;
            }

            weatherCardsContainer.getChildren().clear();
            statusLabel.setText("Fetching METAR, TAF, and runway data...");

            runAsync(
                    () -> weatherService.fetchAirportWeather(input),
                    weather -> {
                        latestWeatherResults = weather;
                        rerenderWeatherCards();
                        statusLabel.setText("");
                        refreshFavoritesBar(weatherAirportInput);
                    },
                    throwable -> statusLabel.setText("Error: " + throwable.getMessage())
            );
        });

        VBox controlsCard = createPanel(
                "Airport Search",
                "Separate multiple ICAO identifiers with commas.",
                new HBox(12, weatherAirportInput, fetchButton)
        );

        VBox favoritesCard = createPanel(
                "Saved Airports",
                "Quick-launch the stations you check most often.",
                favoritesBar
        );
        refreshFavoritesBar(weatherAirportInput);

        ScrollPane resultsScroll = new ScrollPane(weatherCardsContainer);
        resultsScroll.setFitToWidth(true);
        resultsScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        resultsScroll.setPrefHeight(560);

        VBox content = new VBox(16, sectionTitle, sectionSubtitle, controlsCard, favoritesCard, statusLabel, resultsScroll);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }

    private ScrollPane buildRouteTab(Stage stage) {
        Label sectionTitle = createSectionTitle("Route Planner");
        Label sectionSubtitle = createSectionSubtitle("Pair current conditions with forecast periods, direct distance, and fuel reserve checks.");

        TextField departureInput = createInputField("Departure ex: KLFK", 220);
        TextField destinationInput = createInputField("Destination ex: KDFW", 220);
        attachAutocomplete(departureInput, stage);
        attachAutocomplete(destinationInput, stage);

        Button planButton = createPrimaryButton("Analyze Route");
        routeStatusLabel = createMutedLabel("");

        planButton.setOnAction(event -> {
            String departure = departureInput.getText().trim().toUpperCase();
            String destination = destinationInput.getText().trim().toUpperCase();

            if (departure.isEmpty() || destination.isEmpty()) {
                routeStatusLabel.setText("Please enter both a departure and destination.");
                return;
            }

            latestRouteDeparture = departure;
            latestRouteDestination = destination;
            routeResultsBox.getChildren().setAll(routeStatusLabel);
            routeStatusLabel.setText("Fetching route weather and forecast data...");

            runAsync(
                    () -> weatherService.fetchAirportWeather(departure + "," + destination),
                    weather -> {
                        latestRouteResults = weather;
                        routeStatusLabel.setText("");
                        rerenderRouteResults();
                    },
                    throwable -> routeStatusLabel.setText("Error: " + throwable.getMessage())
            );
        });

        VBox plannerCard = createPanel(
                "Direct Route Setup",
                "CloudDeck assumes a direct course and uses the selected aircraft's cruise speed and fuel burn.",
                new HBox(12, departureInput, destinationInput, planButton)
        );

        VBox content = new VBox(16, sectionTitle, sectionSubtitle, plannerCard, routeStatusLabel, routeResultsBox);
        content.setPadding(new Insets(24));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }

    private ScrollPane buildAircraftTab() {
        Label sectionTitle = createSectionTitle("Aircraft Hangar");
        Label sectionSubtitle = createSectionSubtitle("Create reusable aircraft profiles for fuel planning and crosswind alerts.");

        Button removeSelectedButton = createSecondaryButton("Remove Selected");
        Label aircraftStatus = createMutedLabel("");

        removeSelectedButton.setOnAction(event -> {
            AircraftProfile selectedProfile = aircraftSelector.getValue();
            if (selectedProfile == null) {
                aircraftStatus.setText("Select a profile to remove.");
                return;
            }

            aircraftProfileRepository.removeProfile(selectedProfile.name());
            reloadAircraftProfiles(null);
            aircraftStatus.setText("Removed profile: " + selectedProfile.name());
            updateAircraftDisplays();
        });

        TextField nameField = createInputField("Aircraft name", 240);
        TextField cruiseField = createInputField("Cruise speed (kts)", 160);
        TextField burnField = createInputField("Fuel burn (gph)", 160);
        TextField usableFuelField = createInputField("Usable fuel (gal)", 160);
        TextField reserveField = createInputField("Reserve target (gal)", 160);
        TextField crosswindField = createInputField("Max crosswind (kts)", 160);
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Notes, equipment, or planning caveats");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);

        Button saveButton = createPrimaryButton("Save Profile");
        saveButton.setOnAction(event -> {
            try {
                AircraftProfile profile = new AircraftProfile(
                        nameField.getText().trim(),
                        Double.parseDouble(cruiseField.getText().trim()),
                        Double.parseDouble(burnField.getText().trim()),
                        Double.parseDouble(usableFuelField.getText().trim()),
                        Double.parseDouble(reserveField.getText().trim()),
                        Double.parseDouble(crosswindField.getText().trim()),
                        notesArea.getText().trim()
                );

                if (profile.name().isBlank()) {
                    aircraftStatus.setText("Aircraft name is required.");
                    return;
                }

                aircraftProfileRepository.saveProfile(profile);
                reloadAircraftProfiles(profile.name());
                updateAircraftDisplays();
                aircraftStatus.setText("Saved profile: " + profile.name());

                nameField.clear();
                cruiseField.clear();
                burnField.clear();
                usableFuelField.clear();
                reserveField.clear();
                crosswindField.clear();
                notesArea.clear();
            } catch (NumberFormatException exception) {
                aircraftStatus.setText("Use valid numbers for speed, fuel, reserve, and crosswind limits.");
            }
        });

        GridPane formGrid = new GridPane();
        formGrid.setHgap(14);
        formGrid.setVgap(12);
        formGrid.add(formLabel("Aircraft"), 0, 0);
        formGrid.add(nameField, 1, 0);
        formGrid.add(formLabel("Cruise"), 0, 1);
        formGrid.add(cruiseField, 1, 1);
        formGrid.add(formLabel("Burn"), 2, 1);
        formGrid.add(burnField, 3, 1);
        formGrid.add(formLabel("Usable Fuel"), 0, 2);
        formGrid.add(usableFuelField, 1, 2);
        formGrid.add(formLabel("Reserve"), 2, 2);
        formGrid.add(reserveField, 3, 2);
        formGrid.add(formLabel("Max Crosswind"), 0, 3);
        formGrid.add(crosswindField, 1, 3);
        formGrid.add(formLabel("Notes"), 0, 4);
        formGrid.add(notesArea, 1, 4, 3, 1);

        VBox selectionCard = createPanel(
                "Active Profile",
                "Switch profiles globally from the header or manage the active one here.",
                new HBox(10, aircraftSelectorPlaceholder(), removeSelectedButton),
                aircraftSummaryBox
        );

        VBox formCard = createPanel(
                "Create or Update Profile",
                "Use real POH numbers or your own conservative planning values.",
                formGrid,
                saveButton,
                aircraftStatus
        );

        VBox content = new VBox(16, sectionTitle, sectionSubtitle, selectionCard, formCard);
        content.setPadding(new Insets(24));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }
    private VBox buildAirportCard(AirportWeather airportWeather) {
        AircraftProfile selectedProfile = aircraftSelector.getValue();
        MetarData metar = airportWeather.metar();
        VBox card = new VBox(10);
        card.setPadding(new Insets(18));
        card.setStyle(panelStyle(SURFACE_BACKGROUND, true));

        String categoryColor = categoryColor(metar.flightCategory());

        Label airportLabel = new Label(metar.airportId());
        airportLabel.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label categoryBadge = createBadge(metar.flightCategory(), categoryColor);
        Label profileBadge = selectedProfile == null
                ? createBadge("No aircraft selected", UNKNOWN_GRAY)
                : createBadge("Aircraft XW limit " + formatOneDecimal(selectedProfile.maxCrosswindKts()) + " kt", ACCENT_GOLD);

        HBox badges = new HBox(8, categoryBadge, profileBadge);
        badges.setAlignment(Pos.CENTER_LEFT);

        Button favoriteButton = createGhostButton(favoritesRepository.isFavorite(metar.airportId()) ? "Saved" : "Save");
        favoriteButton.setOnAction(event -> toggleFavorite(metar.airportId(), favoriteButton));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, airportLabel, badges, spacer, favoriteButton);
        header.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = makeInfoLabel(metar.airportName());
        nameLabel.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 15px;");

        FlowPane metricStrip = new FlowPane();
        metricStrip.setHgap(10);
        metricStrip.setVgap(10);
        metricStrip.getChildren().addAll(
                createMetricCard("Wind", metar.windGust() > 0
                        ? String.format("%03d deg @ %dG%d kt", metar.windDir(), metar.windSpeed(), metar.windGust())
                        : String.format("%03d deg @ %d kt", metar.windDir(), metar.windSpeed()), categoryColor),
                createMetricCard("Visibility", String.format("%.1f SM", metar.visibilitySm()), ACCENT_BLUE),
                createMetricCard("Altimeter", String.format("%.2f inHg", metar.altimeterInHg()), ACCENT_GOLD),
                createMetricCard("Temperature", String.format("%.1f F", (metar.tempC() * 9 / 5) + 32), SUCCESS_GREEN)
        );

        VfrAssessment vfrAssessment = flightConditionEvaluator.assessVfr(metar);
        Label vfrLabel = createStatusLine(vfrAssessment.message(), vfrAssessment.level());

        Label detailsLabel = makeInfoLabel(
                "Clouds: " + metar.cloudLayersSummary() + "  |  Observation: " + metar.observationTime().replace("T", " ").replace(".000Z", "Z")
        );

        VBox tafSection = buildTafSection(airportWeather.taf());
        VBox runwaySection = buildRunwaySection(metar, airportWeather.runways(), categoryColor, selectedProfile);

        Label rawLabel = makeInfoLabel("Raw METAR: " + metar.rawObservation());
        rawLabel.setStyle("-fx-text-fill: #7f95ab; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        rawLabel.setWrapText(true);

        card.getChildren().addAll(header, nameLabel, metricStrip, vfrLabel, detailsLabel, tafSection, runwaySection, rawLabel);
        return card;
    }

    private VBox buildTafSection(TafData taf) {
        VBox section = new VBox(6);
        section.getChildren().add(new Separator());
        section.getChildren().add(createSubsectionTitle("TAF Outlook"));

        if (taf == null) {
            section.getChildren().add(createMutedLabel("TAF unavailable for this airport."));
            return section;
        }

        VfrAssessment tafAssessment = flightConditionEvaluator.assessTaf(taf);
        section.getChildren().add(createStatusLine(
                tafAssessment == null ? "Forecast available." : tafAssessment.message(),
                tafAssessment == null ? VfrStatusLevel.VFR : tafAssessment.level()
        ));

        Label validityLabel = createMutedLabel("Issued " + taf.issueTime() + "  |  Valid " + taf.validPeriod());
        section.getChildren().add(validityLabel);

        int periodsToShow = Math.min(3, taf.periods().size());
        for (int index = 0; index < periodsToShow; index++) {
            TafPeriod period = taf.periods().get(index);
            VfrAssessment assessment = assessTafPeriod(period);
            Label periodLabel = makeInfoLabel(formatTafPeriod(period, assessment));
            periodLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 12px;");
            periodLabel.setWrapText(true);
            section.getChildren().add(periodLabel);
        }

        if (taf.periods().size() > periodsToShow) {
            section.getChildren().add(createMutedLabel("Additional forecast groups omitted for brevity."));
        }

        Label rawTafLabel = makeInfoLabel("Raw TAF: " + taf.rawText());
        rawTafLabel.setStyle("-fx-text-fill: #7f95ab; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        rawTafLabel.setWrapText(true);
        section.getChildren().add(rawTafLabel);
        return section;
    }

    private VBox buildRunwaySection(MetarData metar, List<Runway> runways, String accentColor, AircraftProfile aircraftProfile) {
        VBox section = new VBox(6);
        section.getChildren().add(new Separator());
        section.getChildren().add(createSubsectionTitle("Runway Suitability"));

        if (runways.isEmpty()) {
            Label noData = createMutedLabel("Runway data not available. Enter a runway heading manually.");
            TextField manualInput = createInputField("ex: 17", 90);
            Label resultLabel = createMutedLabel("");
            Button calculateButton = createSecondaryButton("Check");

            calculateButton.setOnAction(event -> {
                try {
                    int runway = Integer.parseInt(manualInput.getText().trim());
                    if (runway < 1 || runway > 36) {
                        resultLabel.setText("Enter a runway from 1 to 36.");
                        return;
                    }

                    CrosswindComponents components = CrosswindCalculator.calculate(runway * 10, metar.windDir(), metar.windSpeed());
                    String result = runwayAnalysisService.formatManualResult(components);
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

        for (RunwayAnalysis analysis : runwayAnalysisService.analyze(metar, runways, aircraftProfile)) {
            String headwindLabel = analysis.components().headwindKts() >= 0
                    ? String.format("HW %.1f", analysis.components().headwindKts())
                    : String.format("TW %.1f", Math.abs(analysis.components().headwindKts()));
            String crosswindLabel = String.format("XW %.1f %s",
                    Math.abs(analysis.components().crosswindKts()),
                    analysis.components().crosswindKts() >= 0 ? "R" : "L");

            String text = analysis.bestOption()
                    ? "Best runway " + analysis.runway().ident() + "  |  " + headwindLabel + "  |  " + crosswindLabel
                    : "Runway " + analysis.runway().ident() + "  |  " + headwindLabel + "  |  " + crosswindLabel;

            if (analysis.exceedsAircraftLimit() && aircraftProfile != null) {
                text += "  |  Above " + formatOneDecimal(aircraftProfile.maxCrosswindKts()) + " kt limit";
            }

            Label runwayLabel = new Label(text);
            runwayLabel.setWrapText(true);
            runwayLabel.setStyle(analysis.exceedsAircraftLimit()
                    ? "-fx-text-fill: " + WARNING_RED + "; -fx-font-size: 12px; -fx-font-weight: bold;"
                    : analysis.bestOption()
                    ? "-fx-text-fill: " + accentColor + "; -fx-font-size: 12px; -fx-font-weight: bold;"
                    : "-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 12px;");
            section.getChildren().add(runwayLabel);
        }

        return section;
    }
    private void rerenderWeatherCards() {
        weatherCardsContainer.getChildren().setAll(
                latestWeatherResults.stream().map(this::buildAirportCard).collect(Collectors.toList())
        );
    }

    private void rerenderRouteResults() {
        routeResultsBox.getChildren().clear();
        if (latestRouteResults.isEmpty() || latestRouteDeparture == null || latestRouteDestination == null) {
            return;
        }

        RouteAssessment assessment = flightConditionEvaluator.assessRoute(
                latestRouteResults,
                latestRouteDeparture,
                latestRouteDestination
        );
        routeResultsBox.getChildren().add(createBanner(assessment.message(), assessment.level()));

        AircraftProfile aircraftProfile = aircraftSelector.getValue();
        if (aircraftProfile != null) {
            AirportInfo departureAirport = airportsRepository.findAirportByIcao(latestRouteDeparture);
            AirportInfo destinationAirport = airportsRepository.findAirportByIcao(latestRouteDestination);
            RoutePlan routePlan = flightPlanningService.planDirectRoute(departureAirport, destinationAirport, aircraftProfile);
            if (routePlan != null) {
                routeResultsBox.getChildren().add(buildRouteSummaryCard(routePlan, aircraftProfile));
            }
        } else {
            routeResultsBox.getChildren().add(createMutedLabel("Select an aircraft profile to unlock fuel and endurance planning."));
        }

        routeResultsBox.getChildren().addAll(
                latestRouteResults.stream().map(this::buildAirportCard).collect(Collectors.toList())
        );
    }

    private VBox buildRouteSummaryCard(RoutePlan routePlan, AircraftProfile aircraftProfile) {
        FlowPane metrics = new FlowPane();
        metrics.setHgap(12);
        metrics.setVgap(12);
        metrics.getChildren().addAll(
                createMetricCard("Distance", formatOneDecimal(routePlan.distanceNm()) + " nm", ACCENT_BLUE),
                createMetricCard("ETE", formatDuration(routePlan.estimatedTimeHours()), SUCCESS_GREEN),
                createMetricCard("Trip Fuel", formatOneDecimal(routePlan.tripFuelGallons()) + " gal", ACCENT_GOLD),
                createMetricCard("Reserve Left", formatOneDecimal(routePlan.reserveRemainingGallons()) + " gal",
                        routePlan.reserveSatisfied() ? SUCCESS_GREEN : WARNING_RED)
        );

        Label note = routePlan.reserveSatisfied()
                ? createMutedLabel(aircraftProfile.name() + " reserve target is satisfied for a direct route.")
                : createMutedLabel("Reserve target is not met for " + aircraftProfile.name() + ". Consider fuel, a stop, or a different aircraft.");
        note.setStyle("-fx-text-fill: " + (routePlan.reserveSatisfied() ? TEXT_MUTED : WARNING_RED) + "; -fx-font-size: 12px;");

        return createPanel(
                "Aircraft Planning Summary",
                routePlan.departureAirport().ident() + " to " + routePlan.destinationAirport().ident() + " using " + aircraftProfile.name(),
                metrics,
                note
        );
    }

    private void refreshFavoritesBar(TextField airportInput) {
        favoritesBar.getChildren().clear();
        List<String> favorites = favoritesRepository.loadFavorites().stream()
                .filter(favorite -> !favorite.contains("->") && !favorite.contains("→"))
                .toList();

        if (favorites.isEmpty()) {
            favoritesBar.getChildren().add(createMutedLabel("No saved airports yet. Save one from an airport card."));
            return;
        }

        FlowPane chips = new FlowPane();
        chips.setHgap(8);
        chips.setVgap(8);

        for (String icao : favorites) {
            HBox chip = new HBox(4);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.06); -fx-border-color: " + BORDER_COLOR + "; " +
                            "-fx-background-radius: 16; -fx-border-radius: 16; -fx-padding: 4px 6px 4px 10px;"
            );

            Button selectButton = new Button(icao);
            selectButton.setStyle("-fx-background-color: transparent; -fx-text-fill: " + ACCENT_BLUE + "; -fx-font-size: 12px;");
            selectButton.setOnAction(event -> airportInput.setText(icao));

            Button removeButton = new Button("x");
            removeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #8ca2b8; -fx-font-size: 11px;");
            removeButton.setOnAction(event -> {
                favoritesRepository.removeFavorite(icao);
                refreshFavoritesBar(airportInput);
            });

            chip.getChildren().addAll(selectButton, removeButton);
            chips.getChildren().add(chip);
        }

        favoritesBar.getChildren().add(chips);
    }
    private void attachAutocomplete(TextField input, Stage stage) {
        ListView<String> suggestionList = new ListView<>();
        suggestionList.setStyle(
                "-fx-background-color: #1a2b3f; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 1px;"
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

            runAsync(
                    () -> airportsRepository.suggestAirports(currentQuery, 6),
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

    private void showSuggestions(TextField input, Stage stage, Popup popup, ListView<String> suggestionList, List<AirportSuggestion> suggestions) {
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
    private void toggleFavorite(String airportId, Button favoriteButton) {
        if (favoritesRepository.isFavorite(airportId)) {
            favoritesRepository.removeFavorite(airportId);
            favoriteButton.setText("Save");
        } else {
            favoritesRepository.addFavorite(airportId);
            favoriteButton.setText("Saved");
        }

        if (weatherAirportInput != null) {
            refreshFavoritesBar(weatherAirportInput);
        }
    }

    private void updateAircraftDisplays() {
        AircraftProfile selectedProfile = aircraftSelector.getValue();
        if (selectedProfile == null) {
            aircraftHeroSummary.setText("No aircraft selected");
            aircraftHeroNote.setText("Runway cards will still load, but fuel planning and personal crosswind alerts stay disabled.");
            aircraftSummaryBox.getChildren().setAll(createMutedLabel("Create or select an aircraft profile to personalize planning."));
            return;
        }

        aircraftHeroSummary.setText(selectedProfile.name() + "  |  Cruise " + formatOneDecimal(selectedProfile.cruiseSpeedKts()) + " kt");
        aircraftHeroNote.setText(
                "Fuel burn " + formatOneDecimal(selectedProfile.fuelBurnGph()) + " gph, usable fuel "
                        + formatOneDecimal(selectedProfile.usableFuelGallons()) + " gal, max crosswind "
                        + formatOneDecimal(selectedProfile.maxCrosswindKts()) + " kt."
        );

        FlowPane metrics = new FlowPane();
        metrics.setHgap(10);
        metrics.setVgap(10);
        metrics.getChildren().addAll(
                createMetricCard("Cruise", formatOneDecimal(selectedProfile.cruiseSpeedKts()) + " kt", ACCENT_BLUE),
                createMetricCard("Burn", formatOneDecimal(selectedProfile.fuelBurnGph()) + " gph", ACCENT_GOLD),
                createMetricCard("Usable Fuel", formatOneDecimal(selectedProfile.usableFuelGallons()) + " gal", SUCCESS_GREEN),
                createMetricCard("Crosswind", formatOneDecimal(selectedProfile.maxCrosswindKts()) + " kt", CAUTION_ORANGE)
        );

        Label notesLabel = createMutedLabel(selectedProfile.notes().isBlank()
                ? "No additional notes saved for this profile."
                : selectedProfile.notes());
        notesLabel.setWrapText(true);

        aircraftSummaryBox.getChildren().setAll(metrics, notesLabel);
    }

    private void reloadAircraftProfiles(String selectedName) {
        List<AircraftProfile> profiles = aircraftProfileRepository.loadProfiles();
        aircraftSelector.getItems().setAll(profiles);
        if (profiles.isEmpty()) {
            aircraftSelector.setValue(null);
            return;
        }

        AircraftProfile selectedProfile = profiles.getFirst();
        if (selectedName != null) {
            for (AircraftProfile profile : profiles) {
                if (profile.name().equalsIgnoreCase(selectedName)) {
                    selectedProfile = profile;
                    break;
                }
            }
        } else if (aircraftSelector.getValue() != null) {
            for (AircraftProfile profile : profiles) {
                if (profile.name().equalsIgnoreCase(aircraftSelector.getValue().name())) {
                    selectedProfile = profile;
                    break;
                }
            }
        }

        aircraftSelector.setValue(selectedProfile);
    }
    private Label createSectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 24px; -fx-font-weight: bold;");
        return label;
    }

    private Label createSectionSubtitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 13px;");
        label.setWrapText(true);
        return label;
    }

    private Label createSubsectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 14px; -fx-font-weight: bold;");
        return label;
    }

    private VBox createPanel(String title, String subtitle, javafx.scene.Node... content) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 12px;");
        subtitleLabel.setWrapText(true);

        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle(panelStyle(SURFACE_BACKGROUND_ALT, true));
        panel.getChildren().addAll(titleLabel, subtitleLabel);
        panel.getChildren().addAll(content);
        return panel;
    }

    private String panelStyle(String background, boolean elevated) {
        return "-fx-background-color: " + background + ";"
                + "-fx-background-radius: 18;"
                + "-fx-border-color: " + BORDER_COLOR + ";"
                + "-fx-border-radius: 18;"
                + "-fx-border-width: 1;"
                + (elevated ? "-fx-effect: " + CARD_SHADOW + ";" : "")
                + "-fx-background-insets: 0;";
    }

    private VBox createMetricCard(String title, String value, String accentColor) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + UNKNOWN_GRAY + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 15px; -fx-font-weight: bold;");

        VBox card = new VBox(4, titleLabel, valueLabel);
        card.setPadding(new Insets(12));
        card.setMinWidth(130);
        card.setStyle(
                "-fx-background-color: rgba(255,255,255,0.05); -fx-border-color: rgba(255,255,255,0.07); " +
                        "-fx-background-radius: 14; -fx-border-radius: 14;"
        );
        return card;
    }

    private Label createStatusLine(String text, VfrStatusLevel level) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle(switch (level) {
            case WARNING -> "-fx-text-fill: " + WARNING_RED + "; -fx-font-size: 13px; -fx-font-weight: bold;";
            case CAUTION -> "-fx-text-fill: " + CAUTION_ORANGE + "; -fx-font-size: 13px; -fx-font-weight: bold;";
            case VFR -> "-fx-text-fill: " + SUCCESS_GREEN + "; -fx-font-size: 13px; -fx-font-weight: bold;";
        });
        return label;
    }

    private VBox createBanner(String text, RouteDecisionLevel level) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 16px; -fx-font-weight: bold;");

        String background = switch (level) {
            case GO -> "rgba(36, 118, 77, 0.35)";
            case CAUTION -> "rgba(167, 102, 28, 0.35)";
            case NO_GO -> "rgba(153, 50, 50, 0.35)";
        };

        VBox banner = new VBox(label);
        banner.setPadding(new Insets(14));
        banner.setStyle(
                "-fx-background-color: " + background + "; -fx-border-color: " + BORDER_COLOR + "; " +
                        "-fx-background-radius: 16; -fx-border-radius: 16;"
        );
        return banner;
    }

    private Label createBadge(String text, String color) {
        Label badge = new Label(text);
        badge.setStyle(
                "-fx-background-color: rgba(255,255,255,0.07); -fx-text-fill: " + color + "; -fx-font-size: 11px; " +
                        "-fx-font-weight: bold; -fx-background-radius: 12; -fx-padding: 4px 10px;"
        );
        return badge;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: linear-gradient(to right, #3ea7f5, #6fd3ff); -fx-text-fill: white; " +
                        "-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10px 18px; -fx-background-radius: 14; -fx-cursor: hand;"
        );
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: rgba(255,255,255,0.08); -fx-border-color: " + BORDER_COLOR + "; -fx-text-fill: " + TEXT_PRIMARY + "; " +
                        "-fx-font-size: 12px; -fx-padding: 9px 16px; -fx-background-radius: 14; -fx-border-radius: 14; -fx-cursor: hand;"
        );
        return button;
    }

    private Button createGhostButton(String text) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: transparent; -fx-border-color: " + BORDER_COLOR + "; -fx-text-fill: " + ACCENT_GOLD + "; " +
                        "-fx-font-size: 12px; -fx-padding: 6px 12px; -fx-background-radius: 14; -fx-border-radius: 14; -fx-cursor: hand;"
        );
        return button;
    }

    private TextField createInputField(String prompt, double maxWidth) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMaxWidth(maxWidth);
        field.setStyle(
                "-fx-background-color: rgba(255,255,255,0.08); -fx-border-color: " + BORDER_COLOR + "; -fx-text-fill: white; " +
                        "-fx-prompt-text-fill: #7f95ab; -fx-font-size: 13px; -fx-padding: 10px 12px; -fx-background-radius: 14; -fx-border-radius: 14;"
        );
        return field;
    }

    private Label createMutedLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + UNKNOWN_GRAY + "; -fx-font-size: 12px;");
        label.setWrapText(true);
        return label;
    }

    private Label makeInfoLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.web(TEXT_PRIMARY));
        label.setFont(Font.font("Arial", 13));
        label.setWrapText(true);
        return label;
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        return label;
    }

    private HBox aircraftSelectorPlaceholder() {
        Label activeLabel = new Label("Active profile is selected in the header");
        activeLabel.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 12px;");
        HBox box = new HBox(activeLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private String categoryColor(String category) {
        return switch (category) {
            case "VFR" -> SUCCESS_GREEN;
            case "MVFR" -> ACCENT_BLUE;
            case "IFR" -> WARNING_RED;
            case "LIFR" -> "#d789ff";
            default -> UNKNOWN_GRAY;
        };
    }

    private VfrAssessment assessTafPeriod(TafPeriod period) {
        TafData syntheticTaf = new TafData("TEMP", "", "", "", List.of(period));
        return flightConditionEvaluator.assessTaf(syntheticTaf);
    }

    private String formatTafPeriod(TafPeriod period, VfrAssessment assessment) {
        StringBuilder builder = new StringBuilder();
        builder.append(period.label()).append(": ").append(assessment == null ? "Forecast available" : assessment.level());

        if (period.visibilitySm() != null) {
            builder.append(" | Vis ").append(formatOneDecimal(period.visibilitySm())).append(" SM");
        }
        if (!period.cloudLayers().isEmpty()) {
            builder.append(" | ").append(period.cloudLayersSummary());
        }
        if (!period.weatherTokens().isEmpty()) {
            builder.append(" | Wx ").append(String.join(" ", period.weatherTokens()));
        }

        return builder.toString();
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

    private <T> void runAsync(CheckedSupplier<T> supplier, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, backgroundExecutor).whenComplete((result, throwable) -> Platform.runLater(() -> {
            if (throwable == null) {
                onSuccess.accept(result);
            } else {
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                onError.accept(cause);
            }
        }));
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
