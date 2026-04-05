package com.kylebarnes.clouddeck.ui;

import com.kylebarnes.clouddeck.data.AviationWeatherClient;
import com.kylebarnes.clouddeck.data.MetarParser;
import com.kylebarnes.clouddeck.data.OurAirportsRepository;
import com.kylebarnes.clouddeck.data.TafClient;
import com.kylebarnes.clouddeck.data.TafParser;
import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AirportDiagramPreview;
import com.kylebarnes.clouddeck.model.AlternateAirportOption;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.AirportSuggestion;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.CrosswindComponents;
import com.kylebarnes.clouddeck.model.DistanceUnit;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.RecentRouteEntry;
import com.kylebarnes.clouddeck.model.RoutePlan;
import com.kylebarnes.clouddeck.model.Runway;
import com.kylebarnes.clouddeck.model.SolarTimes;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;
import com.kylebarnes.clouddeck.model.TemperatureUnit;
import com.kylebarnes.clouddeck.model.ThemePreset;
import com.kylebarnes.clouddeck.model.TimedRouteAssessment;
import com.kylebarnes.clouddeck.service.CrosswindCalculator;
import com.kylebarnes.clouddeck.service.DensityAltitudeAssessment;
import com.kylebarnes.clouddeck.service.DensityAltitudeService;
import com.kylebarnes.clouddeck.service.AlternateAirportService;
import com.kylebarnes.clouddeck.service.AirportDiagramService;
import com.kylebarnes.clouddeck.service.BriefingExportService;
import com.kylebarnes.clouddeck.service.FaaChartLinkService;
import com.kylebarnes.clouddeck.service.FlightConditionEvaluator;
import com.kylebarnes.clouddeck.service.FlightPlanningService;
import com.kylebarnes.clouddeck.service.OperationalAlert;
import com.kylebarnes.clouddeck.service.OperationalAlertService;
import com.kylebarnes.clouddeck.service.RouteAssessment;
import com.kylebarnes.clouddeck.service.RouteDecisionLevel;
import com.kylebarnes.clouddeck.service.RunwayAnalysis;
import com.kylebarnes.clouddeck.service.RunwayAnalysisService;
import com.kylebarnes.clouddeck.service.SolarCalculatorService;
import com.kylebarnes.clouddeck.service.VfrAssessment;
import com.kylebarnes.clouddeck.service.VfrStatusLevel;
import com.kylebarnes.clouddeck.service.WeatherService;
import com.kylebarnes.clouddeck.storage.AircraftProfileRepository;
import com.kylebarnes.clouddeck.storage.FavoritesRepository;
import com.kylebarnes.clouddeck.storage.LocalAircraftProfileRepository;
import com.kylebarnes.clouddeck.storage.LocalFavoritesRepository;
import com.kylebarnes.clouddeck.storage.LocalRouteHistoryRepository;
import com.kylebarnes.clouddeck.storage.LocalSettingsRepository;
import com.kylebarnes.clouddeck.storage.RouteHistoryRepository;
import com.kylebarnes.clouddeck.storage.SettingsRepository;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class MainApp extends Application {
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
    private final BriefingExportService briefingExportService = new BriefingExportService();
    private final FaaChartLinkService faaChartLinkService = new FaaChartLinkService();
    private final AirportDiagramService airportDiagramService = new AirportDiagramService();
    private final OperationalAlertService operationalAlertService = new OperationalAlertService();
    private final DensityAltitudeService densityAltitudeService = new DensityAltitudeService();
    private final AlternateAirportService alternateAirportService = new AlternateAirportService(
            airportsRepository,
            weatherService,
            flightConditionEvaluator,
            runwayAnalysisService
    );
    private final SolarCalculatorService solarCalculatorService = new SolarCalculatorService();
    private final FavoritesRepository favoritesRepository = new LocalFavoritesRepository();
    private final AircraftProfileRepository aircraftProfileRepository = new LocalAircraftProfileRepository();
    private final SettingsRepository settingsRepository = new LocalSettingsRepository();
    private final RouteHistoryRepository routeHistoryRepository = new LocalRouteHistoryRepository();
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("clouddeck-background");
        return thread;
    });

    private final VBox favoritesBar = new VBox(6);
    private final VBox weatherCardsContainer = new VBox(14);
    private final VBox routeResultsBox = new VBox(12);
    private final VBox recentRoutesBox = new VBox(8);
    private final VBox aircraftSummaryBox = new VBox(8);
    private final ComboBox<AircraftProfile> aircraftSelector = new ComboBox<>();
    private final Label aircraftHeroSummary = new Label();
    private final Label aircraftHeroNote = new Label();
    private static final DateTimeFormatter ROUTE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter CLOCK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private final Map<String, Image> airportDiagramImageCache = new ConcurrentHashMap<>();

    private ThemePalette themePalette;
    private AppSettings appSettings;
    private Stage primaryStage;
    private Scene scene;
    private TextField weatherAirportInput;
    private Label routeStatusLabel;
    private TextField routeDepartureInput;
    private TextField routeDestinationInput;
    private TextField routeDepartureTimeInput;
    private List<AirportWeather> latestWeatherResults = List.of();
    private List<AirportWeather> latestRouteResults = List.of();
    private List<AlternateAirportOption> latestAlternateOptions = List.of();
    private boolean alternateSuggestionsLoading;
    private String alternateSuggestionsStatus = "";
    private String settingsStatusMessage = "";
    private String latestRouteDeparture;
    private String latestRouteDestination;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        appSettings = settingsRepository.loadSettings();
        themePalette = ThemePalette.forPreset(appSettings.themePreset());
        reloadAircraftProfiles(appSettings.defaultAircraftName());
        aircraftSelector.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateAircraftDisplays();
            invalidateAlternateSuggestions();
            rerenderWeatherCards();
            rerenderRouteResults();
        });

        BorderPane root = buildRoot(stage);
        scene = new Scene(root, 1100, 820);
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

    private BorderPane buildRoot(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add(appSettings.themePreset() == ThemePreset.CLEARSKY ? "theme-clearsky" : "theme-nightfall");
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, " + themePalette.appBackground() + ", " + themePalette.appBackgroundAlt() + ");");
        root.setTop(buildAppHeader());

        TabPane tabPane = new TabPane(
                new Tab("Weather", buildWeatherTab(stage)),
                new Tab("Route Planner", buildRouteTab(stage)),
                new Tab("Aircraft", buildAircraftTab()),
                new Tab("Settings", buildSettingsTab(stage))
        );
        tabPane.getTabs().forEach(tab -> tab.setClosable(false));
        tabPane.setStyle("-fx-background-color: transparent;");
        root.setCenter(tabPane);
        return root;
    }

    private void rebuildScene(String weatherInput, String routeDeparture, String routeDestination, String routeTime) {
        BorderPane root = buildRoot(primaryStage);
        scene.setRoot(root);

        if (weatherAirportInput != null) {
            if (!weatherInput.isBlank()) {
                weatherAirportInput.setText(weatherInput);
            } else if (!appSettings.homeAirport().isBlank()) {
                weatherAirportInput.setText(appSettings.homeAirport());
            }
        }
        if (routeDepartureInput != null && !routeDeparture.isBlank()) {
            routeDepartureInput.setText(routeDeparture);
        }
        if (routeDestinationInput != null && !routeDestination.isBlank()) {
            routeDestinationInput.setText(routeDestination);
        }
        if (routeDepartureTimeInput != null && !routeTime.isBlank()) {
            routeDepartureTimeInput.setText(routeTime);
        }

        updateAircraftDisplays();
        rerenderWeatherCards();
        rerenderRouteResults();
    }

    private VBox buildAppHeader() {
        VBox header = new VBox(18);
        header.setPadding(new Insets(26, 28, 18, 28));
        header.setStyle("-fx-background-color: rgba(7, 13, 22, 0.24);");

        Label eyebrow = new Label("Pilot briefing workspace");
        eyebrow.setStyle("-fx-text-fill: " + themePalette.accentGold() + "; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label title = new Label("CloudDeck");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 38));
        title.setTextFill(Color.web(themePalette.textPrimary()));

        Label subtitle = new Label("Weather, forecasts, runway suitability, and fuel planning in one cockpit-friendly workflow.");
        subtitle.setStyle("-fx-text-fill: " + themePalette.textMuted() + "; -fx-font-size: 14px;");
        subtitle.setWrapText(true);

        VBox branding = new VBox(8, eyebrow, title, subtitle);
        branding.setMaxWidth(540);

        Label selectorLabel = new Label("Selected Aircraft");
        selectorLabel.setStyle("-fx-text-fill: " + themePalette.unknownGray() + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        aircraftSelector.setPromptText("Choose an aircraft profile");
        aircraftSelector.setPrefWidth(320);
        aircraftSelector.setStyle(
                "-fx-background-color: " + themePalette.controlBackground() + "; -fx-border-color: " + themePalette.borderColor() + "; " +
                        "-fx-text-fill: " + themePalette.textPrimary() + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-padding: 4px;"
        );

        aircraftHeroSummary.setStyle("-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        aircraftHeroNote.setStyle("-fx-text-fill: " + themePalette.textMuted() + "; -fx-font-size: 12px;");
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
        if (!appSettings.homeAirport().isBlank()) {
            weatherAirportInput.setText(appSettings.homeAirport());
        }
        attachAutocomplete(weatherAirportInput, stage);

        Button fetchButton = createPrimaryButton("Load Briefing");
        Button homeButton = createSecondaryButton("Use Home");
        homeButton.setOnAction(event -> {
            if (!appSettings.homeAirport().isBlank()) {
                weatherAirportInput.setText(appSettings.homeAirport());
            }
        });
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
                new HBox(12, weatherAirportInput, fetchButton, homeButton)
        );

        VBox favoritesCard = createPanel(
                "Saved Airports",
                "Quick-launch the stations you check most often.",
                favoritesBar
        );
        refreshFavoritesBar(weatherAirportInput);

        VBox content = new VBox(16, sectionTitle, sectionSubtitle, controlsCard, favoritesCard, statusLabel, weatherCardsContainer);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }

    private ScrollPane buildRouteTab(Stage stage) {
        Label sectionTitle = createSectionTitle("Route Planner");
        Label sectionSubtitle = createSectionSubtitle("Pair current conditions with forecast periods, route assumptions, and reserve checks.");

        routeDepartureInput = createInputField("Departure ex: KLFK", 220);
        if (latestRouteDeparture != null && !latestRouteDeparture.isBlank()) {
            routeDepartureInput.setText(latestRouteDeparture);
        } else if (!appSettings.homeAirport().isBlank()) {
            routeDepartureInput.setText(appSettings.homeAirport());
        }
        routeDestinationInput = createInputField("Destination ex: KDFW", 220);
        if (latestRouteDestination != null && !latestRouteDestination.isBlank()) {
            routeDestinationInput.setText(latestRouteDestination);
        }
        routeDepartureTimeInput = createInputField("Departure UTC yyyy-MM-dd HH:mm", 240);
        routeDepartureTimeInput.setText(routeDepartureTimeInput.getText().isBlank()
                ? LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0).format(ROUTE_TIME_FORMATTER)
                : routeDepartureTimeInput.getText());
        attachAutocomplete(routeDepartureInput, stage);
        attachAutocomplete(routeDestinationInput, stage);

        Button planButton = createPrimaryButton("Analyze Route");
        Button exportButton = createSecondaryButton("Export Briefing");
        routeStatusLabel = createMutedLabel("");
        refreshRecentRoutes();

        planButton.setOnAction(event -> {
            analyzeRouteFromInputs();
        });

        exportButton.setOnAction(event -> {
            if (latestRouteResults.isEmpty() || latestRouteDeparture == null || latestRouteDestination == null) {
                routeStatusLabel.setText("Analyze a route before exporting a briefing.");
                return;
            }

            AircraftProfile aircraftProfile = aircraftSelector.getValue();
            AirportInfo departureAirport = airportsRepository.findAirportByIcao(latestRouteDeparture);
            AirportInfo destinationAirport = airportsRepository.findAirportByIcao(latestRouteDestination);
            AirportWeather departureWeather = findRouteWeather(latestRouteDeparture);
            AirportWeather destinationWeather = findRouteWeather(latestRouteDestination);
            RouteAssessment routeAssessment = flightConditionEvaluator.assessRoute(
                    latestRouteResults,
                    latestRouteDeparture,
                    latestRouteDestination,
                    appSettings
            );

            RoutePlan routePlan = aircraftProfile == null
                    ? null
                    : flightPlanningService.planDirectRoute(departureAirport, destinationAirport, aircraftProfile, appSettings);
            TimedRouteAssessment timedRouteAssessment = null;
            LocalDateTime departureTimeUtc = getPlannedDepartureTimeUtc();
            if (routePlan != null && departureTimeUtc != null) {
                timedRouteAssessment = flightConditionEvaluator.assessTimedRoute(
                        departureWeather,
                        destinationWeather,
                        departureTimeUtc,
                        departureTimeUtc.plusMinutes((long) Math.round(routePlan.estimatedTimeHours() * 60)),
                        appSettings
                );
            }

            try {
                List<OperationalAlert> alerts = collectRouteOperationalAlerts(
                        routePlan,
                        aircraftProfile,
                        departureWeather,
                        destinationWeather,
                        routeAssessment,
                        timedRouteAssessment
                );
                Path exportPath = briefingExportService.exportRouteBriefing(
                        routePlan,
                        aircraftProfile,
                        latestRouteResults,
                        routeAssessment,
                        timedRouteAssessment,
                        alerts,
                        latestAlternateOptions,
                        appSettings
                );
                routeStatusLabel.setText("Briefing exported to " + exportPath);
            } catch (Exception exception) {
                routeStatusLabel.setText("Could not export briefing: " + exception.getMessage());
            }
        });

        VBox plannerCard = createPanel(
                "Direct Route Setup",
                "Enter a route, set departure time, then review the decision summary before diving into airport details.",
                buildRoutePlannerControls(planButton, exportButton)
        );
        VBox recentRoutesCard = createPanel(
                "Recent Routes",
                "Reuse recent route checks with their saved UTC departure time.",
                recentRoutesBox
        );

        HBox topRow = new HBox(16, plannerCard, recentRoutesCard);
        plannerCard.setMaxWidth(Double.MAX_VALUE);
        recentRoutesCard.setPrefWidth(340);
        HBox.setHgrow(plannerCard, Priority.ALWAYS);

        VBox content = new VBox(16, sectionTitle, sectionSubtitle, topRow, routeStatusLabel, routeResultsBox);
        content.setPadding(new Insets(24));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }

    private ScrollPane buildSettingsTab(Stage stage) {
        Label sectionTitle = createSectionTitle("Settings");
        Label sectionSubtitle = createSectionSubtitle("Define defaults and thresholds that future planning features can reuse.");

        TextField homeAirportField = createInputField("Home airport ICAO", 220);
        homeAirportField.setText(appSettings.homeAirport());
        attachAutocomplete(homeAirportField, stage);

        ComboBox<ThemePreset> themePresetBox = new ComboBox<>();
        themePresetBox.getItems().setAll(ThemePreset.values());
        themePresetBox.setValue(appSettings.themePreset());
        themePresetBox.setPrefWidth(220);

        ComboBox<TemperatureUnit> temperatureUnitBox = new ComboBox<>();
        temperatureUnitBox.getItems().setAll(TemperatureUnit.values());
        temperatureUnitBox.setValue(appSettings.temperatureUnit());
        temperatureUnitBox.setPrefWidth(220);

        ComboBox<DistanceUnit> distanceUnitBox = new ComboBox<>();
        distanceUnitBox.getItems().setAll(DistanceUnit.values());
        distanceUnitBox.setValue(appSettings.distanceUnit());
        distanceUnitBox.setPrefWidth(220);

        ComboBox<AircraftProfile> defaultAircraftBox = new ComboBox<>();
        defaultAircraftBox.getItems().setAll(aircraftProfileRepository.loadProfiles());
        defaultAircraftBox.setPrefWidth(260);
        for (AircraftProfile profile : defaultAircraftBox.getItems()) {
            if (profile.name().equalsIgnoreCase(appSettings.defaultAircraftName())) {
                defaultAircraftBox.setValue(profile);
                break;
            }
        }
        if (defaultAircraftBox.getValue() == null && !defaultAircraftBox.getItems().isEmpty()) {
            defaultAircraftBox.setValue(defaultAircraftBox.getItems().getFirst());
        }

        TextField warningVisibilityField = createInputField("Warning visibility SM", 160);
        warningVisibilityField.setText(String.valueOf(appSettings.vfrWarningVisibilitySm()));
        TextField warningCeilingField = createInputField("Warning ceiling ft", 160);
        warningCeilingField.setText(String.valueOf(appSettings.vfrWarningCeilingFt()));
        TextField cautionVisibilityField = createInputField("Caution visibility SM", 160);
        cautionVisibilityField.setText(String.valueOf(appSettings.vfrCautionVisibilitySm()));
        TextField cautionCeilingField = createInputField("Caution ceiling ft", 160);
        cautionCeilingField.setText(String.valueOf(appSettings.vfrCautionCeilingFt()));
        TextField densityCautionField = createInputField("Density caution ft", 160);
        densityCautionField.setText(String.valueOf(appSettings.densityAltitudeCautionFt()));
        TextField densityWarningField = createInputField("Density warning ft", 160);
        densityWarningField.setText(String.valueOf(appSettings.densityAltitudeWarningFt()));
        TextField taxiFuelField = createInputField("Taxi fuel gal", 160);
        taxiFuelField.setText(formatOneDecimal(appSettings.taxiFuelGallons()));
        TextField climbFuelField = createInputField("Climb fuel gal", 160);
        climbFuelField.setText(formatOneDecimal(appSettings.climbFuelGallons()));
        TextField groundspeedAdjustmentField = createInputField("Groundspeed adjustment kt", 160);
        groundspeedAdjustmentField.setText(String.valueOf(appSettings.groundspeedAdjustmentKts()));

        Label settingsStatus = createMutedLabel(settingsStatusMessage);
        Button saveSettingsButton = createPrimaryButton("Save Settings");
        saveSettingsButton.setOnAction(event -> {
            try {
                AircraftProfile defaultProfile = defaultAircraftBox.getValue();
                String savedWeatherInput = weatherAirportInput == null ? "" : weatherAirportInput.getText();
                String savedRouteDeparture = routeDepartureInput == null ? "" : routeDepartureInput.getText();
                String savedRouteDestination = routeDestinationInput == null ? "" : routeDestinationInput.getText();
                String savedRouteTime = routeDepartureTimeInput == null ? "" : routeDepartureTimeInput.getText();
                AppSettings updatedSettings = new AppSettings(
                        homeAirportField.getText().trim().toUpperCase(),
                        defaultProfile == null ? "" : defaultProfile.name(),
                        themePresetBox.getValue(),
                        temperatureUnitBox.getValue(),
                        distanceUnitBox.getValue(),
                        Double.parseDouble(taxiFuelField.getText().trim()),
                        Double.parseDouble(climbFuelField.getText().trim()),
                        Integer.parseInt(groundspeedAdjustmentField.getText().trim()),
                        Float.parseFloat(warningVisibilityField.getText().trim()),
                        Integer.parseInt(warningCeilingField.getText().trim()),
                        Float.parseFloat(cautionVisibilityField.getText().trim()),
                        Integer.parseInt(cautionCeilingField.getText().trim()),
                        Integer.parseInt(densityCautionField.getText().trim()),
                        Integer.parseInt(densityWarningField.getText().trim())
                );

                appSettings = updatedSettings;
                themePalette = ThemePalette.forPreset(appSettings.themePreset());
                settingsRepository.saveSettings(appSettings);
                reloadAircraftProfiles(appSettings.defaultAircraftName());
                invalidateAlternateSuggestions();
                settingsStatusMessage = "Settings saved.";
                rebuildScene(savedWeatherInput, savedRouteDeparture, savedRouteDestination, savedRouteTime);
            } catch (NumberFormatException exception) {
                settingsStatusMessage = "";
                settingsStatus.setText("Use valid numbers for thresholds and route assumptions.");
            }
        });

        GridPane defaultsGrid = new GridPane();
        defaultsGrid.setHgap(14);
        defaultsGrid.setVgap(12);
        defaultsGrid.add(formLabel("Home Airport"), 0, 0);
        defaultsGrid.add(homeAirportField, 1, 0);
        defaultsGrid.add(formLabel("Default Aircraft"), 0, 1);
        defaultsGrid.add(defaultAircraftBox, 1, 1);
        defaultsGrid.add(formLabel("Theme"), 0, 2);
        defaultsGrid.add(themePresetBox, 1, 2);
        defaultsGrid.add(formLabel("Temperature Unit"), 0, 3);
        defaultsGrid.add(temperatureUnitBox, 1, 3);
        defaultsGrid.add(formLabel("Distance Unit"), 0, 4);
        defaultsGrid.add(distanceUnitBox, 1, 4);

        GridPane thresholdsGrid = new GridPane();
        thresholdsGrid.setHgap(14);
        thresholdsGrid.setVgap(12);
        thresholdsGrid.add(formLabel("VFR Warning Vis"), 0, 0);
        thresholdsGrid.add(warningVisibilityField, 1, 0);
        thresholdsGrid.add(formLabel("VFR Warning Ceiling"), 2, 0);
        thresholdsGrid.add(warningCeilingField, 3, 0);
        thresholdsGrid.add(formLabel("VFR Caution Vis"), 0, 1);
        thresholdsGrid.add(cautionVisibilityField, 1, 1);
        thresholdsGrid.add(formLabel("VFR Caution Ceiling"), 2, 1);
        thresholdsGrid.add(cautionCeilingField, 3, 1);
        thresholdsGrid.add(formLabel("DA Caution"), 0, 2);
        thresholdsGrid.add(densityCautionField, 1, 2);
        thresholdsGrid.add(formLabel("DA Warning"), 2, 2);
        thresholdsGrid.add(densityWarningField, 3, 2);

        GridPane planningGrid = new GridPane();
        planningGrid.setHgap(14);
        planningGrid.setVgap(12);
        planningGrid.add(formLabel("Taxi Fuel"), 0, 0);
        planningGrid.add(taxiFuelField, 1, 0);
        planningGrid.add(formLabel("Climb Fuel"), 2, 0);
        planningGrid.add(climbFuelField, 3, 0);
        planningGrid.add(formLabel("Groundspeed Adj"), 0, 1);
        planningGrid.add(groundspeedAdjustmentField, 1, 1);

        VBox defaultsCard = createPanel(
                "Defaults",
                "Set values the app should restore at launch.",
                defaultsGrid
        );
        VBox thresholdsCard = createPanel(
                "Thresholds",
                "These feed current VFR and density altitude advisories.",
                thresholdsGrid
        );
        VBox planningCard = createPanel(
                "Route Assumptions",
                "Applied to direct-route fuel and ETA calculations for every aircraft profile.",
                planningGrid
        );

        VBox content = new VBox(16, sectionTitle, sectionSubtitle, defaultsCard, thresholdsCard, planningCard, saveSettingsButton, settingsStatus);
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
        AirportInfo airportInfo = airportWeather.airportInfo();
        MetarData metar = airportWeather.metar();
        VBox card = new VBox(10);
        card.setPadding(new Insets(18));
        card.setStyle(panelStyle(themePalette.surfaceBackground(), true));

        String categoryColor = categoryColor(metar.flightCategory());

        Label airportLabel = new Label(metar.airportId());
        airportLabel.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label categoryBadge = createBadge(metar.flightCategory(), categoryColor);
        Label profileBadge = selectedProfile == null
                ? createBadge("No aircraft selected", themePalette.unknownGray())
                : createBadge("Aircraft XW limit " + formatOneDecimal(selectedProfile.maxCrosswindKts()) + " kt", themePalette.accentGold());

        HBox badges = new HBox(8, categoryBadge, profileBadge);
        badges.setAlignment(Pos.CENTER_LEFT);

        Button favoriteButton = createGhostButton(favoritesRepository.isFavorite(metar.airportId()) ? "Saved" : "Save");
        favoriteButton.setOnAction(event -> toggleFavorite(metar.airportId(), favoriteButton));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, airportLabel, badges, spacer, favoriteButton);
        header.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = makeInfoLabel(metar.airportName());
        nameLabel.setStyle("-fx-text-fill: " + themePalette.textMuted() + "; -fx-font-size: 15px;");

        FlowPane metricStrip = new FlowPane();
        metricStrip.setHgap(10);
        metricStrip.setVgap(10);
        metricStrip.getChildren().addAll(
                createMetricCard("Wind", metar.windGust() > 0
                        ? String.format("%03d deg @ %dG%d kt", metar.windDir(), metar.windSpeed(), metar.windGust())
                        : String.format("%03d deg @ %d kt", metar.windDir(), metar.windSpeed()), categoryColor),
                createMetricCard("Visibility", String.format("%.1f SM", metar.visibilitySm()), themePalette.accentBlue()),
                createMetricCard("Altimeter", String.format("%.2f inHg", metar.altimeterInHg()), themePalette.accentGold()),
                createMetricCard("Temperature", formatTemperature(metar.tempC()), themePalette.successGreen())
        );

        VfrAssessment vfrAssessment = flightConditionEvaluator.assessVfr(metar, appSettings);
        Label vfrLabel = createStatusLine(vfrAssessment.message(), vfrAssessment.level());

        Label detailsLabel = makeInfoLabel(
                "Clouds: " + metar.cloudLayersSummary() + "  |  Observation: " + metar.observationTime().replace("T", " ").replace(".000Z", "Z")
        );

        VBox airportBriefingSection = buildAirportBriefingSection(airportInfo, metar);
        VBox tafSection = buildTafSection(airportWeather.taf());
        VBox runwaySection = buildRunwaySection(metar, airportWeather.runways(), categoryColor, selectedProfile);

        Label rawLabel = makeInfoLabel("Raw METAR: " + metar.rawObservation());
        rawLabel.setStyle("-fx-text-fill: #7f95ab; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        rawLabel.setWrapText(true);

        card.getChildren().addAll(header, nameLabel, metricStrip, vfrLabel, detailsLabel, airportBriefingSection, tafSection, runwaySection, rawLabel);
        return card;
    }

    private VBox buildAirportBriefingSection(AirportInfo airportInfo, MetarData metar) {
        VBox section = new VBox(6);
        section.getChildren().add(new Separator());
        section.getChildren().add(createSubsectionTitle("Airport Briefing"));

        if (airportInfo == null) {
            section.getChildren().add(createMutedLabel("Airport details unavailable for this station."));
            return section;
        }

        String airportType = airportInfo.airportType().replace('_', ' ');
        Label basics = makeInfoLabel(
                airportInfo.municipality() + ", " + airportInfo.isoRegion() + "  |  " +
                        airportType + "  |  Field elev " + airportInfo.elevationFt() + " ft"
        );
        basics.setStyle("-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 12px;");
        section.getChildren().add(basics);

        DensityAltitudeAssessment densityAssessment = densityAltitudeService.assess(airportInfo, metar, appSettings);
        if (densityAssessment != null) {
            Label densityLine = createStatusLine(
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

        LocalDate briefingDate = extractUtcDate(metar.observationTime());
        SolarTimes solarTimes = solarCalculatorService.calculate(airportInfo, briefingDate);
        if (solarTimes != null) {
            LocalDateTime observationTimeUtc = extractUtcDateTime(metar.observationTime());
            String solarSummary = formatSolarSummary(solarTimes);
            if (observationTimeUtc != null && observationTimeUtc.toLocalDate().equals(solarTimes.dateUtc())) {
                solarSummary += "  |  Observation " + (solarCalculatorService.isDaylight(solarTimes, observationTimeUtc)
                        ? "in daylight"
                        : "at night");
            }
            section.getChildren().add(createMutedLabel(solarSummary));
        }

        section.getChildren().add(buildChartResourcesBox(metar.airportId()));

        return section;
    }

    private VBox buildChartResourcesBox(String airportId) {
        VBox chartBox = new VBox(6);
        chartBox.getChildren().add(createSubsectionTitle("Chart Resources"));

        chartBox.getChildren().add(buildAirportDiagramPreviewBox(airportId));

        Button diagramButton = createSecondaryButton("Airport Diagram");
        diagramButton.setOnAction(event -> openExternalUrl(faaChartLinkService.buildAirportDiagramUrl(airportId)));

        Button supplementButton = createSecondaryButton("Chart Supplement");
        supplementButton.setOnAction(event -> openExternalUrl(faaChartLinkService.buildChartSupplementUrl(airportId)));

        HBox actions = new HBox(10, diagramButton, supplementButton);
        Label hint = createMutedLabel(
                "Opens official FAA chart results in your browser. " + faaChartLinkService.airportSearchHint(airportId)
        );
        chartBox.getChildren().addAll(actions, hint);
        return chartBox;
    }

    private VBox buildAirportDiagramPreviewBox(String airportId) {
        VBox previewBox = new VBox(6);

        Label previewTitle = createMutedLabel("Airport diagram preview");
        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(250);
        imageView.setSmooth(true);

        Label statusLabel = createMutedLabel("Loading FAA airport diagram preview...");

        VBox imageFrame = new VBox(8, imageView, statusLabel);
        imageFrame.setPadding(new Insets(10));
        imageFrame.setStyle(
                "-fx-background-color: " + themePalette.metricBackground() + "; -fx-border-color: " + themePalette.metricBorder() + "; " +
                        "-fx-background-radius: 14; -fx-border-radius: 14;"
        );

        Image cachedImage = airportDiagramImageCache.get(airportId);
        if (cachedImage != null) {
            imageView.setImage(cachedImage);
            statusLabel.setText("Click the image to open the full FAA PDF.");
            imageView.setOnMouseClicked(event -> openAirportDiagramPdf(airportId));
        } else {
            runAsync(
                    () -> airportDiagramService.loadPreview(airportId),
                    preview -> {
                        if (preview == null) {
                            statusLabel.setText("FAA airport diagram preview unavailable for this airport.");
                            return;
                        }

                        try {
                            Image image = new Image(preview.imagePath().toUri().toString(), true);
                            airportDiagramImageCache.put(airportId, image);
                            imageView.setImage(image);
                            imageView.setOnMouseClicked(event -> openExternalUrl(preview.pdfUrl()));
                            statusLabel.setText("Click the image to open the full FAA PDF.");
                        } catch (Exception exception) {
                            statusLabel.setText("Diagram loaded, but the preview image could not be displayed.");
                        }
                    },
                    throwable -> statusLabel.setText("Could not load FAA diagram preview: " + throwable.getMessage())
            );
        }

        previewBox.getChildren().addAll(previewTitle, imageFrame);
        return previewBox;
    }

    private VBox buildTafSection(TafData taf) {
        VBox section = new VBox(6);
        section.getChildren().add(new Separator());
        section.getChildren().add(createSubsectionTitle("TAF Outlook"));

        if (taf == null) {
            section.getChildren().add(createMutedLabel("TAF unavailable for this airport."));
            return section;
        }

        VfrAssessment tafAssessment = flightConditionEvaluator.assessTaf(taf, appSettings);
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
            periodLabel.setStyle("-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 12px;");
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
                    ? "-fx-text-fill: " + themePalette.warningRed() + "; -fx-font-size: 12px; -fx-font-weight: bold;"
                    : analysis.bestOption()
                    ? "-fx-text-fill: " + accentColor + "; -fx-font-size: 12px; -fx-font-weight: bold;"
                    : "-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 12px;");
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

        AircraftProfile aircraftProfile = aircraftSelector.getValue();
        RouteAssessment assessment = flightConditionEvaluator.assessRoute(
                latestRouteResults,
                latestRouteDeparture,
                latestRouteDestination,
                appSettings
        );
        VBox routeBanner = createBanner(assessment.message(), assessment.level());

        LocalDateTime departureTimeUtc = null;
        TimedRouteAssessment timedRouteAssessment = null;
        if (aircraftProfile != null) {
            AirportInfo departureAirport = airportsRepository.findAirportByIcao(latestRouteDeparture);
            AirportInfo destinationAirport = airportsRepository.findAirportByIcao(latestRouteDestination);
            AirportWeather departureWeather = latestRouteResults.stream()
                    .filter(weather -> weather.metar().airportId().equalsIgnoreCase(latestRouteDeparture))
                    .findFirst()
                    .orElse(null);
            AirportWeather destinationWeather = latestRouteResults.stream()
                    .filter(weather -> weather.metar().airportId().equalsIgnoreCase(latestRouteDestination))
                    .findFirst()
                    .orElse(null);
            RoutePlan routePlan = flightPlanningService.planDirectRoute(departureAirport, destinationAirport, aircraftProfile, appSettings);
            if (routePlan != null) {
                departureTimeUtc = getPlannedDepartureTimeUtc();
                if (departureTimeUtc != null) {
                    timedRouteAssessment = flightConditionEvaluator.assessTimedRoute(
                            departureWeather,
                            destinationWeather,
                            departureTimeUtc,
                            departureTimeUtc.plusMinutes((long) Math.round(routePlan.estimatedTimeHours() * 60)),
                            appSettings
                    );
                    routeBanner = createBanner(timedRouteAssessment.message(), timedRouteAssessment.level());
                }
                routeResultsBox.getChildren().add(buildRouteDecisionDashboard(
                        routeBanner,
                        routePlan,
                        aircraftProfile,
                        departureWeather,
                        destinationWeather,
                        assessment,
                        timedRouteAssessment
                ));
                routeResultsBox.getChildren().add(buildOperationalAlertsCard(
                        routePlan,
                        aircraftProfile,
                        departureWeather,
                        destinationWeather,
                        assessment,
                        timedRouteAssessment
                ));
            } else {
                routeResultsBox.getChildren().add(routeBanner);
            }
        } else {
            routeResultsBox.getChildren().add(routeBanner);
            routeResultsBox.getChildren().add(createMutedLabel("Select an aircraft profile to unlock fuel and endurance planning."));
        }

        if (timedRouteAssessment != null && timedRouteAssessment.level() != RouteDecisionLevel.GO) {
            maybeLoadAlternateSuggestions();
        } else if (assessment.level() != RouteDecisionLevel.GO) {
            maybeLoadAlternateSuggestions();
        }

        if (alternateSuggestionsLoading) {
            routeResultsBox.getChildren().add(createPanel(
                    "Alternate Airports",
                    "Searching nearby airports with better conditions and runway fit.",
                    createMutedLabel("Loading alternates...")
            ));
        } else if (!latestAlternateOptions.isEmpty()) {
            routeResultsBox.getChildren().add(buildAlternatesCard());
        } else if (!alternateSuggestionsStatus.isBlank()) {
            routeResultsBox.getChildren().add(createPanel(
                    "Alternate Airports",
                    "Nearby fallback options for the destination.",
                    createMutedLabel(alternateSuggestionsStatus)
            ));
        }

        routeResultsBox.getChildren().add(buildRouteAirportDetailsCard());
    }

    private VBox buildRouteDecisionDashboard(
            VBox routeBanner,
            RoutePlan routePlan,
            AircraftProfile aircraftProfile,
            AirportWeather departureWeather,
            AirportWeather destinationWeather,
            RouteAssessment assessment,
            TimedRouteAssessment timedRouteAssessment
    ) {
        VBox summaryCard = buildRouteSummaryCard(routePlan, aircraftProfile);
        summaryCard.setMaxWidth(Double.MAX_VALUE);

        VBox timingCard = createPanel(
                "Decision Snapshot",
                "Fast route-level view before reviewing airport detail sections.",
                routeBanner,
                buildDecisionSnapshotMetrics(routePlan, departureWeather, destinationWeather, assessment, timedRouteAssessment)
        );
        timingCard.setPrefWidth(340);

        HBox row = new HBox(16, summaryCard, timingCard);
        HBox.setHgrow(summaryCard, Priority.ALWAYS);

        VBox wrapper = new VBox(row);
        return wrapper;
    }

    private FlowPane buildDecisionSnapshotMetrics(
            RoutePlan routePlan,
            AirportWeather departureWeather,
            AirportWeather destinationWeather,
            RouteAssessment assessment,
            TimedRouteAssessment timedRouteAssessment
    ) {
        FlowPane metrics = new FlowPane();
        metrics.setHgap(10);
        metrics.setVgap(10);

        VfrAssessment departureCurrent = departureWeather == null ? null : flightConditionEvaluator.assessVfr(departureWeather.metar(), appSettings);
        VfrAssessment destinationCurrent = destinationWeather == null ? null : flightConditionEvaluator.assessVfr(destinationWeather.metar(), appSettings);

        metrics.getChildren().add(createMetricCard(
                "Departure",
                departureCurrent == null ? "N/A" : departureCurrent.level().name(),
                statusAccentColor(departureCurrent == null ? VfrStatusLevel.WARNING : departureCurrent.level())
        ));
        metrics.getChildren().add(createMetricCard(
                "Destination",
                destinationCurrent == null ? "N/A" : destinationCurrent.level().name(),
                statusAccentColor(destinationCurrent == null ? VfrStatusLevel.WARNING : destinationCurrent.level())
        ));

        if (timedRouteAssessment != null) {
            metrics.getChildren().add(createMetricCard(
                    "Planned Window",
                    timedRouteAssessment.level().name(),
                    decisionAccentColor(timedRouteAssessment.level())
            ));
        } else {
            metrics.getChildren().add(createMetricCard(
                    "Route Outlook",
                    assessment.level().name(),
                    decisionAccentColor(assessment.level())
            ));
        }

        metrics.getChildren().add(createMetricCard(
                "Alternates",
                latestAlternateOptions.isEmpty() ? "None" : String.valueOf(latestAlternateOptions.size()),
                latestAlternateOptions.isEmpty() ? themePalette.textMuted() : themePalette.cautionOrange()
        ));

        return metrics;
    }

    private VBox buildRouteAirportDetailsCard() {
        AirportWeather departureWeather = findRouteWeather(latestRouteDeparture);
        AirportWeather destinationWeather = findRouteWeather(latestRouteDestination);

        VBox departureCard = buildCompactRouteAirportCard("Departure Detail", departureWeather);
        VBox destinationCard = buildCompactRouteAirportCard("Destination Detail", destinationWeather);

        HBox row = new HBox(16, departureCard, destinationCard);
        HBox.setHgrow(departureCard, Priority.ALWAYS);
        HBox.setHgrow(destinationCard, Priority.ALWAYS);

        return createPanel(
                "Airport Details",
                "Expanded weather, runway, and chart context for each end of the route.",
                row
        );
    }

    private VBox buildCompactRouteAirportCard(String title, AirportWeather airportWeather) {
        if (airportWeather == null) {
            return createPanel(title, "No airport data available.", createMutedLabel("This side of the route could not be loaded."));
        }

        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setStyle(panelStyle(themePalette.surfaceBackground(), true));

        MetarData metar = airportWeather.metar();
        AircraftProfile selectedProfile = aircraftSelector.getValue();

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + themePalette.textMuted() + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label airportLabel = new Label(metar.airportId() + " - " + metar.airportName());
        airportLabel.setStyle("-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 18px; -fx-font-weight: bold;");
        airportLabel.setWrapText(true);

        HBox badges = new HBox(8, createBadge(metar.flightCategory(), categoryColor(metar.flightCategory())));
        if (selectedProfile != null) {
            badges.getChildren().add(createBadge("XW " + formatOneDecimal(selectedProfile.maxCrosswindKts()) + " kt", themePalette.accentGold()));
        }

        FlowPane metrics = new FlowPane();
        metrics.setHgap(10);
        metrics.setVgap(10);
        metrics.getChildren().addAll(
                createMetricCard("Wind", metar.windGust() > 0
                        ? String.format("%03d/%dG%d", metar.windDir(), metar.windSpeed(), metar.windGust())
                        : String.format("%03d/%d", metar.windDir(), metar.windSpeed()), categoryColor(metar.flightCategory())),
                createMetricCard("Vis", String.format("%.1f SM", metar.visibilitySm()), themePalette.accentBlue()),
                createMetricCard("Alt", String.format("%.2f", metar.altimeterInHg()), themePalette.accentGold()),
                createMetricCard("Temp", formatTemperature(metar.tempC()), themePalette.successGreen())
        );

        VBox sections = new VBox(
                10,
                buildAirportBriefingSection(airportWeather.airportInfo(), metar),
                buildTafSection(airportWeather.taf()),
                buildRunwaySection(metar, airportWeather.runways(), categoryColor(metar.flightCategory()), selectedProfile)
        );

        card.getChildren().addAll(titleLabel, airportLabel, badges, metrics, sections);
        return card;
    }

    private VBox buildAlternatesCard() {
        VBox alternatesList = new VBox(10);
        AircraftProfile selectedAircraft = aircraftSelector.getValue();

        for (AlternateAirportOption option : latestAlternateOptions) {
            AirportWeather weather = option.airportWeather();
            String summary = weather.airportInfo() == null
                    ? weather.metar().airportId()
                    : weather.airportInfo().ident() + " - " + weather.airportInfo().name();

            Label title = new Label(summary);
            title.setStyle("-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 14px; -fx-font-weight: bold;");

            Label badges = new Label(
                    weather.metar().flightCategory() + "  |  "
                            + formatDistance(option.distanceFromDestinationNm())
                            + " from destination  |  "
                            + option.vfrAssessment().level()
            );
            badges.setStyle("-fx-text-fill: " + themePalette.textMuted() + "; -fx-font-size: 12px;");

            String runwayLine = "No runway suitability data available.";
            List<RunwayAnalysis> runwayAnalysis = runwayAnalysisService.analyze(weather.metar(), weather.runways(), selectedAircraft);
            if (!runwayAnalysis.isEmpty()) {
                RunwayAnalysis best = runwayAnalysis.getFirst();
                runwayLine = "Best runway " + best.runway().ident()
                        + " with crosswind "
                        + formatOneDecimal(Math.abs(best.components().crosswindKts()))
                        + " kt";
                if (best.exceedsAircraftLimit() && selectedAircraft != null) {
                    runwayLine += " (above selected aircraft limit)";
                }
            }

            Label summaryLabel = createMutedLabel(option.summary());
            Label runwayLabel = createMutedLabel(runwayLine);

            VBox optionCard = new VBox(4, title, badges, summaryLabel, runwayLabel);
            optionCard.setPadding(new Insets(12));
            optionCard.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.04); -fx-border-color: rgba(255,255,255,0.06); " +
                            "-fx-background-radius: 14; -fx-border-radius: 14;"
            );
            alternatesList.getChildren().add(optionCard);
        }

        return createPanel(
                "Alternate Airports",
                "Nearby options ranked by weather and runway fit.",
                alternatesList
        );
    }

    private VBox buildOperationalAlertsCard(
            RoutePlan routePlan,
            AircraftProfile aircraftProfile,
            AirportWeather departureWeather,
            AirportWeather destinationWeather,
            RouteAssessment routeAssessment,
            TimedRouteAssessment timedRouteAssessment
    ) {
        List<OperationalAlert> alerts = collectRouteOperationalAlerts(
                routePlan,
                aircraftProfile,
                departureWeather,
                destinationWeather,
                routeAssessment,
                timedRouteAssessment
        );

        if (alerts.isEmpty()) {
            return createPanel(
                    "Operational Alerts",
                    "CloudDeck summarizes route-specific risks here.",
                    createMutedLabel("No additional operational alerts for the current assumptions.")
            );
        }

        VBox alertList = new VBox(10);
        for (OperationalAlert alert : alerts) {
            Label title = new Label(alert.title());
            title.setWrapText(true);
            title.setStyle(switch (alert.level()) {
                case WARNING -> "-fx-text-fill: " + themePalette.warningRed() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
                case CAUTION -> "-fx-text-fill: " + themePalette.cautionOrange() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
                case VFR -> "-fx-text-fill: " + themePalette.successGreen() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
            });

            Label detail = createMutedLabel(alert.detail());
            VBox item = new VBox(4, title, detail);
            item.setPadding(new Insets(12));
            item.setStyle(
                    "-fx-background-color: " + themePalette.metricBackground() + "; -fx-border-color: " + themePalette.metricBorder() + "; " +
                            "-fx-background-radius: 14; -fx-border-radius: 14;"
            );
            alertList.getChildren().add(item);
        }

        return createPanel(
                "Operational Alerts",
                "Centralized warnings for weather, runway fit, fuel, and daylight timing.",
                alertList
        );
    }

    private List<OperationalAlert> collectRouteOperationalAlerts(
            RoutePlan routePlan,
            AircraftProfile aircraftProfile,
            AirportWeather departureWeather,
            AirportWeather destinationWeather,
            RouteAssessment routeAssessment,
            TimedRouteAssessment timedRouteAssessment
    ) {
        List<RunwayAnalysis> departureRunways = departureWeather == null
                ? List.of()
                : runwayAnalysisService.analyze(departureWeather.metar(), departureWeather.runways(), aircraftProfile);
        List<RunwayAnalysis> destinationRunways = destinationWeather == null
                ? List.of()
                : runwayAnalysisService.analyze(destinationWeather.metar(), destinationWeather.runways(), aircraftProfile);

        boolean departureInDaylight = true;
        boolean arrivalInDaylight = true;
        if (routePlan != null) {
            LocalDateTime departureTimeUtc = getPlannedDepartureTimeUtc();
            LocalDateTime arrivalTimeUtc = departureTimeUtc == null
                    ? null
                    : departureTimeUtc.plusMinutes((long) Math.round(routePlan.estimatedTimeHours() * 60));
            if (departureTimeUtc != null) {
                SolarTimes departureSolar = solarCalculatorService.calculate(routePlan.departureAirport(), departureTimeUtc.toLocalDate());
                if (departureSolar != null) {
                    departureInDaylight = solarCalculatorService.isDaylight(departureSolar, departureTimeUtc);
                }
            }
            if (arrivalTimeUtc != null) {
                SolarTimes destinationSolar = solarCalculatorService.calculate(routePlan.destinationAirport(), arrivalTimeUtc.toLocalDate());
                if (destinationSolar != null) {
                    arrivalInDaylight = solarCalculatorService.isDaylight(destinationSolar, arrivalTimeUtc);
                }
            }
        }

        return operationalAlertService.buildRouteAlerts(
                routePlan,
                aircraftProfile,
                departureWeather,
                destinationWeather,
                departureRunways,
                destinationRunways,
                routeAssessment,
                timedRouteAssessment,
                departureInDaylight,
                arrivalInDaylight,
                !latestAlternateOptions.isEmpty(),
                appSettings
        );
    }

    private VBox buildRouteSummaryCard(RoutePlan routePlan, AircraftProfile aircraftProfile) {
        FlowPane metrics = new FlowPane();
        metrics.setHgap(12);
        metrics.setVgap(12);
        metrics.getChildren().addAll(
                createMetricCard("Distance", formatDistance(routePlan.distanceNm()), themePalette.accentBlue()),
                createMetricCard("Groundspeed", formatOneDecimal(routePlan.groundspeedKts()) + " kt", themePalette.cautionOrange()),
                createMetricCard("ETE", formatDuration(routePlan.estimatedTimeHours()), themePalette.successGreen()),
                createMetricCard("Trip Fuel", formatOneDecimal(routePlan.tripFuelGallons()) + " gal", themePalette.accentGold()),
                createMetricCard("Reserve Left", formatOneDecimal(routePlan.reserveRemainingGallons()) + " gal",
                        routePlan.reserveSatisfied() ? themePalette.successGreen() : themePalette.warningRed())
        );

        AirportInfo departureAirport = routePlan.departureAirport();
        AirportInfo destinationAirport = routePlan.destinationAirport();
        AirportWeather departureWeather = latestRouteResults.stream()
                .filter(weather -> weather.metar().airportId().equalsIgnoreCase(departureAirport.ident()))
                .findFirst()
                .orElse(null);
        AirportWeather destinationWeather = latestRouteResults.stream()
                .filter(weather -> weather.metar().airportId().equalsIgnoreCase(destinationAirport.ident()))
                .findFirst()
                .orElse(null);

        DensityAltitudeAssessment departureDensity = departureWeather == null ? null
                : densityAltitudeService.assess(departureAirport, departureWeather.metar(), appSettings);
        DensityAltitudeAssessment destinationDensity = destinationWeather == null ? null
                : densityAltitudeService.assess(destinationAirport, destinationWeather.metar(), appSettings);

        Label note = routePlan.reserveSatisfied()
                ? createMutedLabel(aircraftProfile.name() + " reserve target is satisfied for a direct route.")
                : createMutedLabel("Reserve target is not met for " + aircraftProfile.name() + ". Consider fuel, a stop, or a different aircraft.");
        note.setStyle("-fx-text-fill: " + (routePlan.reserveSatisfied() ? themePalette.textMuted() : themePalette.warningRed()) + "; -fx-font-size: 12px;");

        LocalDateTime departureTimeUtc = getPlannedDepartureTimeUtc();
        LocalDateTime arrivalTimeUtc = departureTimeUtc == null
                ? null
                : departureTimeUtc.plusMinutes((long) Math.round(routePlan.estimatedTimeHours() * 60));
        if (departureTimeUtc != null && arrivalTimeUtc != null) {
            metrics.getChildren().add(createMetricCard("Departure UTC", departureTimeUtc.format(ROUTE_TIME_FORMATTER), themePalette.accentGold()));
            metrics.getChildren().add(createMetricCard("Arrival UTC",
                    arrivalTimeUtc.format(ROUTE_TIME_FORMATTER),
                    themePalette.accentBlue()));
        }

        VBox densityBox = new VBox(4);
        densityBox.getChildren().add(createSubsectionTitle("Performance Snapshot"));
        if (departureDensity != null) {
            densityBox.getChildren().add(createMutedLabel(
                    departureAirport.ident() + " density altitude: " + departureDensity.densityAltitudeFt() + " ft"
            ));
        }
        if (destinationDensity != null) {
            densityBox.getChildren().add(createMutedLabel(
                    destinationAirport.ident() + " density altitude: " + destinationDensity.densityAltitudeFt() + " ft"
            ));
        }
        densityBox.getChildren().add(createMutedLabel(
                "Assumptions: taxi " + formatOneDecimal(routePlan.taxiFuelGallons()) + " gal, climb "
                        + formatOneDecimal(routePlan.climbFuelGallons()) + " gal, airborne burn "
                        + formatOneDecimal(routePlan.airborneFuelGallons()) + " gal."
        ));
        densityBox.getChildren().add(createMutedLabel(
                "Groundspeed uses aircraft cruise " + formatOneDecimal(aircraftProfile.cruiseSpeedKts()) + " kt with "
                        + (appSettings.groundspeedAdjustmentKts() >= 0 ? "+" : "")
                        + appSettings.groundspeedAdjustmentKts() + " kt adjustment."
        ));

        if (departureTimeUtc != null && arrivalTimeUtc != null) {
            SolarTimes departureSolar = solarCalculatorService.calculate(departureAirport, departureTimeUtc.toLocalDate());
            SolarTimes destinationSolar = solarCalculatorService.calculate(destinationAirport, arrivalTimeUtc.toLocalDate());

            VBox solarBox = new VBox(4);
            solarBox.getChildren().add(createSubsectionTitle("Daylight Snapshot"));
            if (departureSolar != null) {
                solarBox.getChildren().add(createMutedLabel(formatSolarPlanningLine(
                        departureAirport.ident(),
                        "Departure",
                        departureSolar,
                        departureTimeUtc
                )));
            }
            if (destinationSolar != null) {
                solarBox.getChildren().add(createMutedLabel(formatSolarPlanningLine(
                        destinationAirport.ident(),
                        "Arrival",
                        destinationSolar,
                        arrivalTimeUtc
                )));
            }

            if (solarBox.getChildren().size() == 1) {
                solarBox.getChildren().add(createMutedLabel("Sunrise and sunset data unavailable for one or more airports."));
            }

            densityBox.getChildren().add(new Separator());
            densityBox.getChildren().add(solarBox);
        }

        return createPanel(
                "Aircraft Planning Summary",
                routePlan.departureAirport().ident() + " to " + routePlan.destinationAirport().ident() + " using " + aircraftProfile.name(),
                metrics,
                densityBox,
                note
        );
    }

    private void refreshRecentRoutes() {
        recentRoutesBox.getChildren().clear();
        List<RecentRouteEntry> recentRoutes = routeHistoryRepository.loadRecentRoutes();
        if (recentRoutes.isEmpty()) {
            recentRoutesBox.getChildren().add(createMutedLabel("No recent routes yet. Analyze a route to save it here."));
            return;
        }

        for (RecentRouteEntry entry : recentRoutes) {
            Label routeLabel = new Label(entry.departureAirport() + " -> " + entry.destinationAirport());
            routeLabel.setStyle("-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 13px; -fx-font-weight: bold;");

            String timestamp = "Saved departure " + entry.plannedDepartureUtc().format(ROUTE_TIME_FORMATTER)
                    + " UTC  |  Last used " + entry.lastUsedUtc().format(ROUTE_TIME_FORMATTER) + " UTC";
            Label metaLabel = createMutedLabel(timestamp);

            Button useButton = createSecondaryButton("Run Saved Route");
            useButton.setOnAction(event -> {
                latestRouteDeparture = entry.departureAirport();
                latestRouteDestination = entry.destinationAirport();
                if (routeDepartureInput != null) {
                    routeDepartureInput.setText(entry.departureAirport());
                }
                if (routeDestinationInput != null) {
                    routeDestinationInput.setText(entry.destinationAirport());
                }
                if (routeDepartureTimeInput != null) {
                    routeDepartureTimeInput.setText(entry.plannedDepartureUtc().format(ROUTE_TIME_FORMATTER));
                }
                analyzeRoute(entry.departureAirport(), entry.destinationAirport(), entry.plannedDepartureUtc());
            });

            HBox header = new HBox(12, routeLabel, new Region(), useButton);
            HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);

            VBox routeCard = new VBox(4, header, metaLabel);
            routeCard.setPadding(new Insets(12));
            routeCard.setStyle(
                    "-fx-background-color: " + themePalette.metricBackground() + "; -fx-border-color: " + themePalette.metricBorder() + "; " +
                            "-fx-background-radius: 14; -fx-border-radius: 14;"
            );
            recentRoutesBox.getChildren().add(routeCard);
        }
    }

    private VBox buildRoutePlannerControls(Button planButton, Button exportButton) {
        VBox fields = new VBox(12);

        VBox departureBox = new VBox(6, formLabel("Departure"), routeDepartureInput);
        VBox destinationBox = new VBox(6, formLabel("Destination"), routeDestinationInput);
        VBox timeBox = new VBox(6, formLabel("Departure UTC"), routeDepartureTimeInput);

        HBox firstRow = new HBox(12, departureBox, destinationBox, timeBox);
        HBox.setHgrow(departureBox, Priority.ALWAYS);
        HBox.setHgrow(destinationBox, Priority.ALWAYS);
        HBox.setHgrow(timeBox, Priority.ALWAYS);

        Label plannerHint = createMutedLabel(
                "Uses the selected aircraft profile plus route assumptions from Settings. Analyze first, then export if needed."
        );
        HBox actionRow = new HBox(10, planButton, exportButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        fields.getChildren().addAll(firstRow, plannerHint, actionRow);
        return fields;
    }

    private void analyzeRouteFromInputs() {
        String departure = routeDepartureInput.getText().trim().toUpperCase();
        String destination = routeDestinationInput.getText().trim().toUpperCase();

        if (departure.isEmpty() || destination.isEmpty()) {
            routeStatusLabel.setText("Please enter both a departure and destination.");
            return;
        }

        LocalDateTime plannedDepartureUtc;
        try {
            plannedDepartureUtc = LocalDateTime.parse(routeDepartureTimeInput.getText().trim(), ROUTE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            routeStatusLabel.setText("Use UTC departure time format yyyy-MM-dd HH:mm.");
            return;
        }

        analyzeRoute(departure, destination, plannedDepartureUtc);
    }

    private void analyzeRoute(String departure, String destination, LocalDateTime plannedDepartureUtc) {
        if (plannedDepartureUtc == null) {
            routeStatusLabel.setText("Use UTC departure time format yyyy-MM-dd HH:mm.");
            return;
        }

        latestRouteDeparture = departure;
        latestRouteDestination = destination;
        if (routeDepartureInput != null) {
            routeDepartureInput.setText(departure);
        }
        if (routeDestinationInput != null) {
            routeDestinationInput.setText(destination);
        }
        if (routeDepartureTimeInput != null) {
            routeDepartureTimeInput.setText(plannedDepartureUtc.format(ROUTE_TIME_FORMATTER));
        }

        routeResultsBox.getChildren().setAll(routeStatusLabel);
        routeStatusLabel.setText("Fetching route weather and forecast data...");

        runAsync(
                () -> weatherService.fetchAirportWeather(departure + "," + destination),
                weather -> {
                    latestRouteResults = weather;
                    routeHistoryRepository.saveRecentRoute(departure, destination, plannedDepartureUtc);
                    invalidateAlternateSuggestions();
                    refreshRecentRoutes();
                    routeStatusLabel.setText("");
                    rerenderRouteResults();
                },
                throwable -> routeStatusLabel.setText("Error: " + throwable.getMessage())
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
                    "-fx-background-color: " + themePalette.metricBackground() + "; -fx-border-color: " + themePalette.borderColor() + "; " +
                            "-fx-background-radius: 16; -fx-border-radius: 16; -fx-padding: 4px 6px 4px 10px;"
            );

            Button selectButton = new Button(icao);
            selectButton.setStyle("-fx-background-color: transparent; -fx-text-fill: " + themePalette.accentBlue() + "; -fx-font-size: 12px;");
            selectButton.setOnAction(event -> airportInput.setText(icao));

            Button removeButton = new Button("x");
            removeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: " + themePalette.favoriteRemoveColor() + "; -fx-font-size: 11px;");
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
                "-fx-background-color: " + themePalette.listBackground() + "; -fx-border-color: " + themePalette.borderColor() + "; -fx-border-width: 1px;"
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

    private void openExternalUrl(String url) {
        try {
            getHostServices().showDocument(url);
        } catch (Exception exception) {
            if (routeStatusLabel != null) {
                routeStatusLabel.setText("Could not open browser: " + exception.getMessage());
            }
        }
    }

    private void openAirportDiagramPdf(String airportId) {
        runAsync(
                () -> airportDiagramService.loadPreview(airportId),
                preview -> {
                    if (preview != null) {
                        openExternalUrl(preview.pdfUrl());
                    }
                },
                throwable -> {
                    if (routeStatusLabel != null) {
                        routeStatusLabel.setText("Could not open airport diagram PDF: " + throwable.getMessage());
                    }
                }
        );
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
                createMetricCard("Cruise", formatOneDecimal(selectedProfile.cruiseSpeedKts()) + " kt", themePalette.accentBlue()),
                createMetricCard("Burn", formatOneDecimal(selectedProfile.fuelBurnGph()) + " gph", themePalette.accentGold()),
                createMetricCard("Usable Fuel", formatOneDecimal(selectedProfile.usableFuelGallons()) + " gal", themePalette.successGreen()),
                createMetricCard("Crosswind", formatOneDecimal(selectedProfile.maxCrosswindKts()) + " kt", themePalette.cautionOrange())
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
        label.setStyle("-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 24px; -fx-font-weight: bold;");
        return label;
    }

    private Label createSectionSubtitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + themePalette.textMuted() + "; -fx-font-size: 13px;");
        label.setWrapText(true);
        return label;
    }

    private Label createSubsectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 14px; -fx-font-weight: bold;");
        return label;
    }

    private VBox createPanel(String title, String subtitle, javafx.scene.Node... content) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-text-fill: " + themePalette.textMuted() + "; -fx-font-size: 12px;");
        subtitleLabel.setWrapText(true);

        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle(panelStyle(themePalette.surfaceBackgroundAlt(), true));
        panel.getChildren().addAll(titleLabel, subtitleLabel);
        panel.getChildren().addAll(content);
        return panel;
    }

    private String panelStyle(String background, boolean elevated) {
        return "-fx-background-color: " + background + ";"
                + "-fx-background-radius: 18;"
                + "-fx-border-color: " + themePalette.borderColor() + ";"
                + "-fx-border-radius: 18;"
                + "-fx-border-width: 1;"
                + (elevated ? "-fx-effect: " + CARD_SHADOW + ";" : "")
                + "-fx-background-insets: 0;";
    }

    private VBox createMetricCard(String title, String value, String accentColor) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + themePalette.unknownGray() + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 15px; -fx-font-weight: bold;");

        VBox card = new VBox(4, titleLabel, valueLabel);
        card.setPadding(new Insets(12));
        card.setMinWidth(130);
        card.setStyle(
                "-fx-background-color: " + themePalette.metricBackground() + "; -fx-border-color: " + themePalette.metricBorder() + "; " +
                        "-fx-background-radius: 14; -fx-border-radius: 14;"
        );
        return card;
    }

    private Label createStatusLine(String text, VfrStatusLevel level) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle(switch (level) {
            case WARNING -> "-fx-text-fill: " + themePalette.warningRed() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
            case CAUTION -> "-fx-text-fill: " + themePalette.cautionOrange() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
            case VFR -> "-fx-text-fill: " + themePalette.successGreen() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
        });
        return label;
    }

    private VBox createBanner(String text, RouteDecisionLevel level) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: " + themePalette.textPrimary() + "; -fx-font-size: 16px; -fx-font-weight: bold;");

        String background = switch (level) {
            case GO -> themePalette.bannerGo();
            case CAUTION -> themePalette.bannerCaution();
            case NO_GO -> themePalette.bannerNoGo();
        };

        VBox banner = new VBox(label);
        banner.setPadding(new Insets(14));
        banner.setStyle(
                "-fx-background-color: " + background + "; -fx-border-color: " + themePalette.borderColor() + "; " +
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
                "-fx-background-color: linear-gradient(to right, " + themePalette.primaryGradientStart() + ", " + themePalette.primaryGradientEnd() + "); -fx-text-fill: white; " +
                        "-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10px 18px; -fx-background-radius: 14; -fx-cursor: hand;"
        );
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: " + themePalette.controlBackground() + "; -fx-border-color: " + themePalette.borderColor() + "; -fx-text-fill: " + themePalette.textPrimary() + "; " +
                        "-fx-font-size: 12px; -fx-padding: 9px 16px; -fx-background-radius: 14; -fx-border-radius: 14; -fx-cursor: hand;"
        );
        return button;
    }

    private Button createGhostButton(String text) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: transparent; -fx-border-color: " + themePalette.borderColor() + "; -fx-text-fill: " + themePalette.accentGold() + "; " +
                        "-fx-font-size: 12px; -fx-padding: 6px 12px; -fx-background-radius: 14; -fx-border-radius: 14; -fx-cursor: hand;"
        );
        return button;
    }

    private TextField createInputField(String prompt, double maxWidth) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMaxWidth(maxWidth);
        field.setStyle(
                "-fx-background-color: " + themePalette.controlBackground() + "; -fx-border-color: " + themePalette.borderColor() + "; -fx-text-fill: " + themePalette.textPrimary() + "; " +
                        "-fx-prompt-text-fill: " + themePalette.unknownGray() + "; -fx-font-size: 13px; -fx-padding: 10px 12px; -fx-background-radius: 14; -fx-border-radius: 14;"
        );
        return field;
    }

    private Label createMutedLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + themePalette.unknownGray() + "; -fx-font-size: 12px;");
        label.setWrapText(true);
        return label;
    }

    private Label makeInfoLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.web(themePalette.textPrimary()));
        label.setFont(Font.font("Arial", 13));
        label.setWrapText(true);
        return label;
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + themePalette.textMuted() + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        return label;
    }

    private HBox aircraftSelectorPlaceholder() {
        Label activeLabel = new Label("Active profile is selected in the header");
        activeLabel.setStyle("-fx-text-fill: " + themePalette.textMuted() + "; -fx-font-size: 12px;");
        HBox box = new HBox(activeLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private String categoryColor(String category) {
        return switch (category) {
            case "VFR" -> themePalette.successGreen();
            case "MVFR" -> themePalette.accentBlue();
            case "IFR" -> themePalette.warningRed();
            case "LIFR" -> "#d789ff";
            default -> themePalette.unknownGray();
        };
    }

    private String statusAccentColor(VfrStatusLevel level) {
        return switch (level) {
            case WARNING -> themePalette.warningRed();
            case CAUTION -> themePalette.cautionOrange();
            case VFR -> themePalette.successGreen();
        };
    }

    private String decisionAccentColor(RouteDecisionLevel level) {
        return switch (level) {
            case GO -> themePalette.successGreen();
            case CAUTION -> themePalette.cautionOrange();
            case NO_GO -> themePalette.warningRed();
        };
    }

    private VfrAssessment assessTafPeriod(TafPeriod period) {
        TafData syntheticTaf = new TafData("TEMP", "", null, "", period.startTimeUtc(), period.endTimeUtc(), "", List.of(period));
        return flightConditionEvaluator.assessTaf(syntheticTaf, appSettings);
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

    private AirportWeather findRouteWeather(String airportId) {
        return latestRouteResults.stream()
                .filter(weather -> weather.metar().airportId().equalsIgnoreCase(airportId))
                .findFirst()
                .orElse(null);
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

    private LocalDateTime getPlannedDepartureTimeUtc() {
        if (routeDepartureTimeInput == null || routeDepartureTimeInput.getText().isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(routeDepartureTimeInput.getText().trim(), ROUTE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String formatTemperature(float tempC) {
        if (appSettings.temperatureUnit() == TemperatureUnit.CELSIUS) {
            return formatOneDecimal(tempC) + " C";
        }
        return formatOneDecimal((tempC * 9 / 5) + 32) + " F";
    }

    private String formatDistance(double distanceNm) {
        if (appSettings.distanceUnit() == DistanceUnit.STATUTE_MILES) {
            return formatOneDecimal(distanceNm * 1.15078) + " mi";
        }
        return formatOneDecimal(distanceNm) + " nm";
    }

    private String formatSolarSummary(SolarTimes solarTimes) {
        if (solarTimes.allDaylight()) {
            return "Sun above horizon all day on " + solarTimes.dateUtc() + " UTC";
        }
        if (solarTimes.allNight()) {
            return "Sun below horizon all day on " + solarTimes.dateUtc() + " UTC";
        }
        return "Sunrise " + solarTimes.sunriseUtc().toLocalTime().format(CLOCK_FORMATTER)
                + " UTC  |  Sunset " + solarTimes.sunsetUtc().toLocalTime().format(CLOCK_FORMATTER) + " UTC";
    }

    private String formatSolarPlanningLine(String airportId, String phase, SolarTimes solarTimes, LocalDateTime timeUtc) {
        String condition = solarCalculatorService.isDaylight(solarTimes, timeUtc) ? "daylight" : "night";
        return airportId + " " + phase + " at " + timeUtc.toLocalTime().format(CLOCK_FORMATTER)
                + " UTC is in " + condition + "  |  " + formatSolarSummary(solarTimes);
    }

    private LocalDateTime extractUtcDateTime(String observationTime) {
        if (observationTime == null || observationTime.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(observationTime).atOffset(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDate extractUtcDate(String observationTime) {
        LocalDateTime observationTimeUtc = extractUtcDateTime(observationTime);
        if (observationTimeUtc != null) {
            return observationTimeUtc.toLocalDate();
        }
        return LocalDate.now(ZoneOffset.UTC);
    }

    private void maybeLoadAlternateSuggestions() {
        if (alternateSuggestionsLoading || !latestAlternateOptions.isEmpty() || !alternateSuggestionsStatus.isBlank()) {
            return;
        }

        AircraftProfile selectedAircraft = aircraftSelector.getValue();
        alternateSuggestionsLoading = true;
        alternateSuggestionsStatus = "";

        runAsync(
                () -> alternateAirportService.suggestAlternates(
                        latestRouteDeparture,
                        latestRouteDestination,
                        selectedAircraft,
                        appSettings,
                        4
                ),
                alternates -> {
                    latestAlternateOptions = alternates;
                    alternateSuggestionsLoading = false;
                    alternateSuggestionsStatus = alternates.isEmpty()
                            ? "No suitable nearby alternates were found within the search radius."
                            : "";
                    rerenderRouteResults();
                },
                throwable -> {
                    latestAlternateOptions = List.of();
                    alternateSuggestionsLoading = false;
                    alternateSuggestionsStatus = "Could not load alternate airports: " + throwable.getMessage();
                    rerenderRouteResults();
                }
        );
    }

    private void invalidateAlternateSuggestions() {
        latestAlternateOptions = List.of();
        alternateSuggestionsLoading = false;
        alternateSuggestionsStatus = "";
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
