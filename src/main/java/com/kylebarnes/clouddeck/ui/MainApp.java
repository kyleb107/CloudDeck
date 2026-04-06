package com.kylebarnes.clouddeck.ui;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.ThemePreset;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.List;
import java.util.Objects;

public class MainApp extends Application {

    private AppContext ctx;
    private UiHelper ui;
    private Stage primaryStage;
    private Scene scene;
    private AppView activeView = AppView.WEATHER;
    private WeatherView weatherView;
    private RouteView routeView;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        ctx = new AppContext();
        ctx.appSettings = ctx.settingsRepository.loadSettings();
        ctx.themePalette = ThemePalette.forPreset(ctx.appSettings.themePreset());
        ui = new UiHelper(ctx);

        reloadAircraftProfiles(ctx.appSettings.defaultAircraftName());
        ctx.aircraftSelector.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateAircraftDisplays();
            ctx.invalidateAlternateSuggestions();
            if (ctx.rerenderWeatherCards != null) ctx.rerenderWeatherCards.run();
            if (ctx.rerenderRouteResults != null) ctx.rerenderRouteResults.run();
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
        ctx.backgroundExecutor.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private BorderPane buildRoot(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add(ctx.appSettings.themePreset() == ThemePreset.CLEARSKY ? "theme-clearsky" : "theme-nightfall");
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, " + ctx.themePalette.appBackground() + ", " + ctx.themePalette.appBackgroundAlt() + ");");
        root.setLeft(buildNavigationRail(stage));

        BorderPane contentShell = new BorderPane();
        contentShell.setTop(buildAppHeader());
        contentShell.setCenter(buildActiveView(stage));
        contentShell.setStyle("-fx-padding: 0 0 0 0;");

        root.setCenter(contentShell);
        return root;
    }

    private VBox buildNavigationRail(Stage stage) {
        StackPane brandLogo = createBrandLogo();

        Label appName = new Label("CloudDeck");
        appName.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 18px; -fx-font-weight: bold; -fx-letter-spacing: 0.6px;");
        Label appSub = new Label("Flight planning console");
        appSub.setStyle("-fx-text-fill: " + ctx.themePalette.textMuted() + "; -fx-font-size: 11px;");

        HBox brandRow = new HBox(12, brandLogo, new VBox(4, appName, appSub));
        brandRow.setAlignment(Pos.CENTER_LEFT);

        VBox navButtons = new VBox(
                10,
                createNavigationButton("WX", "Weather", AppView.WEATHER, stage),
                createNavigationButton("FPL", "Route Planner", AppView.ROUTE, stage),
                createNavigationButton("AC", "Aircraft", AppView.AIRCRAFT, stage),
                createNavigationButton("CFG", "Settings", AppView.SETTINGS, stage)
        );

        VBox missionCard = new VBox(
                6,
                ui.createRailEyebrow("Mode"),
                ui.createRailValue(activeView.displayName()),
                ui.createMutedLabel("Use the left rail to move between briefing, planning, aircraft, and app configuration.")
        );
        missionCard.setPadding(new Insets(14));
        missionCard.setStyle(
                "-fx-background-color: " + ctx.themePalette.metricBackground() + "; -fx-border-color: " + ctx.themePalette.metricBorder() + ";" +
                        "-fx-background-radius: 18; -fx-border-radius: 18;"
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox rail = new VBox(18, brandRow, missionCard, navButtons, spacer, buildRailFooter());
        rail.setPadding(new Insets(24, 18, 24, 18));
        rail.setPrefWidth(260);
        rail.setStyle(
                "-fx-background-color: linear-gradient(to bottom, " + ctx.themePalette.surfaceBackgroundAlt() + ", " + ctx.themePalette.surfaceBackground() + ");" +
                        "-fx-border-color: transparent " + ctx.themePalette.borderColor() + " transparent transparent; -fx-border-width: 0 1 0 0;"
        );
        return rail;
    }

    private VBox buildRailFooter() {
        VBox footer = new VBox(
                6,
                ui.createRailEyebrow("Display"),
                ui.createRailValue(ctx.appSettings.timeDisplayMode().displayName() + " / " + ctx.appSettings.windUnit().displayName()),
                ui.createMutedLabel(ctx.appSettings.homeAirport().isBlank() ? "Home airport not set" : "Home " + ctx.appSettings.homeAirport())
        );
        footer.setPadding(new Insets(14));
        footer.setStyle(
                "-fx-background-color: " + ctx.themePalette.insetBackground() + "; -fx-border-color: " + ctx.themePalette.insetBorder() + ";" +
                        "-fx-background-radius: 18; -fx-border-radius: 18;"
        );
        return footer;
    }

    private StackPane createBrandLogo() {
        StackPane logo = new StackPane();
        logo.setMinSize(58, 58);
        logo.setPrefSize(58, 58);
        logo.setMaxSize(58, 58);
        logo.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, " + ctx.themePalette.primaryGradientStart() + ", " + ctx.themePalette.primaryGradientEnd() + ");" +
                        "-fx-background-radius: 20; -fx-border-color: rgba(255,255,255,0.18); -fx-border-width: 1; -fx-border-radius: 20;"
        );

        SVGPath cloud = new SVGPath();
        cloud.setContent("M18 36 C13 36 9 33 9 28 C9 23 13 20 18 20 C20 14 25 10 31 10 C39 10 45 15 47 23 C52 23 56 27 56 32 C56 38 51 42 44 42 H20 C16 42 13 40 12 37 Z");
        cloud.setFill(Color.web("#ffffff", 0.92));
        cloud.setScaleX(0.86);
        cloud.setScaleY(0.86);
        cloud.setTranslateY(2);

        SVGPath airplane = new SVGPath();
        airplane.setContent("M11 31 L23 31 L39 21 L43 23 L32 31 L45 31 L53 27 L56 29 L49 34 L56 39 L53 41 L45 37 L32 37 L43 45 L39 47 L23 37 L11 37 L15 34 Z");
        airplane.setFill(Color.web(ctx.themePalette.appBackground()));
        airplane.setScaleX(0.72);
        airplane.setScaleY(0.72);
        airplane.setTranslateX(1);
        airplane.setTranslateY(2);

        logo.getChildren().addAll(cloud, airplane);
        return logo;
    }

    private Button createNavigationButton(String code, String label, AppView view, Stage stage) {
        boolean selected = activeView == view;

        Label codeLabel = new Label(code);
        codeLabel.setStyle("-fx-text-fill: " + (selected ? ctx.themePalette.textPrimary() : ctx.themePalette.accentGold()) + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-letter-spacing: 1.2px;");

        Label titleLabel = new Label(label);
        titleLabel.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label subLabel = new Label(view.tagline());
        subLabel.setStyle("-fx-text-fill: " + ctx.themePalette.textMuted() + "; -fx-font-size: 11px;");

        VBox textBlock = new VBox(3, codeLabel, titleLabel, subLabel);
        HBox content = new HBox(textBlock);
        content.setAlignment(Pos.CENTER_LEFT);

        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle(
                "-fx-background-color: " + (selected ? ctx.themePalette.metricBackground() : "transparent") + ";" +
                        "-fx-border-color: " + (selected ? ctx.themePalette.accentBlue() : ctx.themePalette.borderColor()) + ";" +
                        "-fx-border-width: 1 1 1 4; -fx-background-radius: 18; -fx-border-radius: 18; -fx-padding: 14px 16px; -fx-cursor: hand;"
        );
        button.setOnAction(event -> switchView(view, stage));
        return button;
    }

    private void switchView(AppView view, Stage stage) {
        if (activeView == view) {
            return;
        }
        activeView = view;
        rebuildScene(
                weatherView == null ? "" : weatherView.getAirportInput() == null ? "" : weatherView.getAirportInput().getText(),
                routeView == null ? "" : routeView.getDepartureInput() == null ? "" : routeView.getDepartureInput().getText(),
                routeView == null ? "" : routeView.getDestinationInput() == null ? "" : routeView.getDestinationInput().getText(),
                routeView == null ? "" : routeView.getDepartureTimeInput() == null ? "" : routeView.getDepartureTimeInput().getText()
        );
    }

    private javafx.scene.Node buildActiveView(Stage stage) {
        return switch (activeView) {
            case WEATHER -> {
                WeatherView wv = new WeatherView(ctx, ui, this::openExternalUrl);
                weatherView = wv;
                yield wv.build(stage);
            }
            case ROUTE -> {
                RouteView rv = new RouteView(ctx, ui, this::openExternalUrl);
                routeView = rv;
                yield rv.build(stage);
            }
            case AIRCRAFT -> new AircraftView(ctx, ui).build();
            case SETTINGS -> {
                String wInput = weatherView == null ? "" : weatherView.getAirportInput() == null ? "" : weatherView.getAirportInput().getText();
                String rDep = routeView == null ? "" : routeView.getDepartureInput() == null ? "" : routeView.getDepartureInput().getText();
                String rDest = routeView == null ? "" : routeView.getDestinationInput() == null ? "" : routeView.getDestinationInput().getText();
                String rTime = routeView == null ? "" : routeView.getDepartureTimeInput() == null ? "" : routeView.getDepartureTimeInput().getText();
                SettingsView sv = new SettingsView(ctx, ui, this::openExternalUrl, () -> {
                    reloadAircraftProfiles(ctx.appSettings.defaultAircraftName());
                    ctx.invalidateAlternateSuggestions();
                    rebuildScene(wInput, rDep, rDest, rTime);
                });
                yield sv.build(stage, wInput, rDep, rDest, rTime);
            }
        };
    }

    private void rebuildScene(String weatherInput, String routeDeparture, String routeDestination, String routeTime) {
        BorderPane root = buildRoot(primaryStage);
        scene.setRoot(root);

        if (weatherView != null && weatherView.getAirportInput() != null) {
            if (!weatherInput.isBlank()) {
                weatherView.getAirportInput().setText(weatherInput);
            } else if (!ctx.appSettings.homeAirport().isBlank()) {
                weatherView.getAirportInput().setText(ctx.appSettings.homeAirport());
            }
        }
        if (routeView != null && routeView.getDepartureInput() != null && !routeDeparture.isBlank()) {
            routeView.getDepartureInput().setText(routeDeparture);
        }
        if (routeView != null && routeView.getDestinationInput() != null && !routeDestination.isBlank()) {
            routeView.getDestinationInput().setText(routeDestination);
        }
        if (routeView != null && routeView.getDepartureTimeInput() != null && !routeTime.isBlank()) {
            routeView.getDepartureTimeInput().setText(routeTime);
        }

        updateAircraftDisplays();
        if (ctx.rerenderWeatherCards != null) ctx.rerenderWeatherCards.run();
        if (ctx.rerenderRouteResults != null) ctx.rerenderRouteResults.run();
    }

    private VBox buildAppHeader() {
        VBox header = new VBox(18);
        header.setPadding(new Insets(26, 28, 18, 28));
        header.setStyle(
                "-fx-background-color: linear-gradient(to right, " + ctx.themePalette.headerOverlay() + ", " + ctx.themePalette.surfaceBackgroundAlt() + ");" +
                        "-fx-border-color: transparent transparent " + ctx.themePalette.borderColor() + " transparent; -fx-border-width: 0 0 1 0;"
        );

        Label eyebrow = new Label("Pilot briefing workspace");
        eyebrow.setStyle("-fx-text-fill: " + ctx.themePalette.accentGold() + "; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label title = new Label("CloudDeck");
        title.setFont(Font.font("Bahnschrift SemiCondensed", FontWeight.BOLD, 38));
        title.setTextFill(Color.web(ctx.themePalette.textPrimary()));

        Label subtitle = new Label("Weather, forecasts, runway suitability, and fuel planning in one cockpit-friendly workflow.");
        subtitle.setStyle("-fx-text-fill: " + ctx.themePalette.textMuted() + "; -fx-font-size: 14px;");
        subtitle.setWrapText(true);

        VBox branding = new VBox(8, eyebrow, title, subtitle);
        branding.setMaxWidth(540);

        Label selectorLabel = new Label("Selected Aircraft");
        selectorLabel.setStyle("-fx-text-fill: " + ctx.themePalette.unknownGray() + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        ctx.aircraftSelector.setPromptText("Choose an aircraft profile");
        ctx.aircraftSelector.setPrefWidth(320);
        ctx.aircraftSelector.setStyle(
                "-fx-background-color: " + ctx.themePalette.controlBackground() + "; -fx-border-color: " + ctx.themePalette.borderColor() + "; " +
                        "-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-padding: 4px;"
        );

        ctx.aircraftHeroSummary.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        ctx.aircraftHeroNote.setStyle("-fx-text-fill: " + ctx.themePalette.textMuted() + "; -fx-font-size: 12px;");
        ctx.aircraftHeroNote.setWrapText(true);

        VBox aircraftCard = ui.createPanel(
                "Aircraft Profile",
                "Runway warnings and route fuel calculations use the active profile.",
                new VBox(10, selectorLabel, ctx.aircraftSelector, ctx.aircraftHeroSummary, ctx.aircraftHeroNote)
        );
        aircraftCard.setMaxWidth(380);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topRow = new HBox(24, branding, spacer, aircraftCard);
        topRow.setAlignment(Pos.TOP_LEFT);
        header.getChildren().addAll(topRow, buildHeaderStatusStrip());
        return header;
    }

    private HBox buildHeaderStatusStrip() {
        HBox strip = new HBox(
                10,
                ui.createHeaderChip("Display", ctx.appSettings.timeDisplayMode().displayName() + " / " + ctx.appSettings.windUnit().displayName()),
                ui.createHeaderChip("Home", ctx.appSettings.homeAirport().isBlank() ? "Unset" : ctx.appSettings.homeAirport()),
                ui.createHeaderChip("Theme", ctx.appSettings.themePreset().displayName()),
                ui.createHeaderChip("Aircraft", ctx.aircraftSelector.getValue() == null ? "No profile" : ctx.aircraftSelector.getValue().name())
        );
        strip.setAlignment(Pos.CENTER_LEFT);
        return strip;
    }

    void updateAircraftDisplays() {
        AircraftProfile selectedProfile = ctx.aircraftSelector.getValue();
        if (selectedProfile == null) {
            ctx.aircraftHeroSummary.setText("No aircraft selected");
            ctx.aircraftHeroNote.setText("Runway cards will still load, but fuel planning and personal crosswind alerts stay disabled.");
            ctx.aircraftSummaryBox.getChildren().setAll(ui.createMutedLabel("Create or select an aircraft profile to personalize planning."));
            return;
        }

        ctx.aircraftHeroSummary.setText(selectedProfile.name() + "  |  Cruise " + ui.formatSpeed(selectedProfile.cruiseSpeedKts()));
        ctx.aircraftHeroNote.setText(
                "Fuel burn " + ui.formatOneDecimal(selectedProfile.fuelBurnGph()) + " gph, usable fuel "
                        + ui.formatOneDecimal(selectedProfile.usableFuelGallons()) + " gal, max crosswind "
                        + ui.formatSpeed(selectedProfile.maxCrosswindKts()) + "."
        );

        javafx.scene.layout.FlowPane metrics = new javafx.scene.layout.FlowPane();
        metrics.setHgap(10);
        metrics.setVgap(10);
        metrics.getChildren().addAll(
                ui.createMetricCard("Cruise", ui.formatSpeed(selectedProfile.cruiseSpeedKts()), ctx.themePalette.accentBlue()),
                ui.createMetricCard("Burn", ui.formatOneDecimal(selectedProfile.fuelBurnGph()) + " gph", ctx.themePalette.accentGold()),
                ui.createMetricCard("Usable Fuel", ui.formatOneDecimal(selectedProfile.usableFuelGallons()) + " gal", ctx.themePalette.successGreen()),
                ui.createMetricCard("Crosswind", ui.formatSpeed(selectedProfile.maxCrosswindKts()), ctx.themePalette.cautionOrange())
        );

        Label notesLabel = ui.createMutedLabel(selectedProfile.notes().isBlank()
                ? "No additional notes saved for this profile."
                : selectedProfile.notes());
        notesLabel.setWrapText(true);

        ctx.aircraftSummaryBox.getChildren().setAll(metrics, notesLabel);
    }

    void reloadAircraftProfiles(String selectedName) {
        List<AircraftProfile> profiles = ctx.aircraftProfileRepository.loadProfiles();
        ctx.aircraftSelector.getItems().setAll(profiles);
        if (profiles.isEmpty()) {
            ctx.aircraftSelector.setValue(null);
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
        } else if (ctx.aircraftSelector.getValue() != null) {
            for (AircraftProfile profile : profiles) {
                if (profile.name().equalsIgnoreCase(ctx.aircraftSelector.getValue().name())) {
                    selectedProfile = profile;
                    break;
                }
            }
        }

        ctx.aircraftSelector.setValue(selectedProfile);
    }

    void toggleFavorite(String airportId, Button favoriteButton) {
        if (ctx.favoritesRepository.isFavorite(airportId)) {
            ctx.favoritesRepository.removeFavorite(airportId);
            favoriteButton.setText("Save");
        } else {
            ctx.favoritesRepository.addFavorite(airportId);
            favoriteButton.setText("Saved");
        }

        if (weatherView != null) {
            weatherView.refreshFavoritesBar(weatherView.getAirportInput());
        }
    }

    void openExternalUrl(String url) {
        try {
            getHostServices().showDocument(url);
        } catch (Exception exception) {
            if (routeView != null && routeView.getStatusLabel() != null) {
                routeView.getStatusLabel().setText("Could not open browser: " + exception.getMessage());
            }
        }
    }

    enum AppView {
        WEATHER("Weather", "Live airport weather"),
        ROUTE("Route Planner", "Flight planning and go/no-go"),
        AIRCRAFT("Aircraft", "Profiles and performance"),
        SETTINGS("Settings", "Display and planning defaults");

        private final String displayName;
        private final String tagline;

        AppView(String displayName, String tagline) {
            this.displayName = displayName;
            this.tagline = tagline;
        }

        String displayName() {
            return displayName;
        }

        String tagline() {
            return tagline;
        }
    }
}
