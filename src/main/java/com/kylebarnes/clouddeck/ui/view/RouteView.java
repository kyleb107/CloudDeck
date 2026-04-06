package com.kylebarnes.clouddeck.ui;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.AlternateAirportOption;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.RecentRouteEntry;
import com.kylebarnes.clouddeck.model.RoutePlan;
import com.kylebarnes.clouddeck.model.Runway;
import com.kylebarnes.clouddeck.model.SolarTimes;
import com.kylebarnes.clouddeck.model.TimeDisplayMode;
import com.kylebarnes.clouddeck.model.TimedRouteAssessment;
import com.kylebarnes.clouddeck.service.BriefingExportResult;
import com.kylebarnes.clouddeck.service.DensityAltitudeAssessment;
import com.kylebarnes.clouddeck.service.OperationalAlert;
import com.kylebarnes.clouddeck.service.RouteAssessment;
import com.kylebarnes.clouddeck.service.RouteDecisionLevel;
import com.kylebarnes.clouddeck.service.RunwayAnalysis;
import com.kylebarnes.clouddeck.service.VfrAssessment;
import com.kylebarnes.clouddeck.service.VfrStatusLevel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;

public class RouteView {

    private final AppContext ctx;
    private final UiHelper ui;
    private final Consumer<String> openUrl;

    TextField routeDepartureInput;
    TextField routeDestinationInput;
    TextField routeDepartureTimeInput;
    ComboBox<TimeDisplayMode> routeTimeModeBox;
    Label routeTimeHelperLabel;
    Label routeStatusLabel;

    public RouteView(AppContext ctx, UiHelper ui, Consumer<String> openUrl) {
        this.ctx = ctx;
        this.ui = ui;
        this.openUrl = openUrl;
    }

    public TextField getDepartureInput() { return routeDepartureInput; }
    public TextField getDestinationInput() { return routeDestinationInput; }
    public TextField getDepartureTimeInput() { return routeDepartureTimeInput; }
    public Label getStatusLabel() { return routeStatusLabel; }

    public ScrollPane build(Stage stage) {
        ctx.rerenderRouteResults = this::rerenderRouteResults;

        Label sectionTitle = ui.createSectionTitle("Route Planner");
        Label sectionSubtitle = ui.createSectionSubtitle("Pair current conditions with forecast periods, route assumptions, and reserve checks.");

        routeDepartureInput = ui.createInputField("Departure ex: KLFK", 220);
        if (ctx.latestRouteDeparture != null && !ctx.latestRouteDeparture.isBlank()) {
            routeDepartureInput.setText(ctx.latestRouteDeparture);
        } else if (!ctx.appSettings.homeAirport().isBlank()) {
            routeDepartureInput.setText(ctx.appSettings.homeAirport());
        }
        routeDestinationInput = ui.createInputField("Destination ex: KDFW", 220);
        if (ctx.latestRouteDestination != null && !ctx.latestRouteDestination.isBlank()) {
            routeDestinationInput.setText(ctx.latestRouteDestination);
        }
        routeTimeModeBox = new ComboBox<>();
        routeTimeModeBox.getItems().setAll(TimeDisplayMode.UTC, TimeDisplayMode.LOCAL);
        routeTimeModeBox.setValue(ctx.appSettings.timeDisplayMode());
        routeTimeModeBox.setPrefWidth(140);
        routeDepartureTimeInput = ui.createInputField("Departure time yyyy-MM-dd HH:mm", 240);
        routeDepartureTimeInput.setText(routeDepartureTimeInput.getText().isBlank()
                ? formatRouteInputValue(
                        LocalDateTime.now(ZoneOffset.UTC).plusHours(1).withMinute(0).withSecond(0).withNano(0),
                        routeTimeModeBox.getValue()
                )
                : routeDepartureTimeInput.getText());
        routeTimeHelperLabel = ui.createMutedLabel("");
        routeDepartureTimeInput.textProperty().addListener((observable, oldValue, newValue) -> updateRouteTimeHelper());
        routeTimeModeBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null && newValue != null && routeDepartureTimeInput != null && !routeDepartureTimeInput.getText().isBlank()) {
                LocalDateTime parsedTimeUtc = parseRouteTimeInput(routeDepartureTimeInput.getText(), oldValue);
                if (parsedTimeUtc != null) {
                    routeDepartureTimeInput.setText(formatRouteInputValue(parsedTimeUtc, newValue));
                }
            }
            updateRouteTimeHelper();
        });
        ui.attachAutocomplete(routeDepartureInput, stage);
        ui.attachAutocomplete(routeDestinationInput, stage);
        updateRouteTimeHelper();

        Button planButton = ui.createPrimaryButton("Analyze Route");
        Button exportButton = ui.createSecondaryButton("Export Briefing");
        routeStatusLabel = ui.createMutedLabel("");
        refreshRecentRoutes();

        planButton.setOnAction(event -> analyzeRouteFromInputs());

        exportButton.setOnAction(event -> {
            if (ctx.latestRouteResults.isEmpty() || ctx.latestRouteDeparture == null || ctx.latestRouteDestination == null) {
                routeStatusLabel.setText("Analyze a route before exporting a briefing.");
                return;
            }

            AircraftProfile aircraftProfile = ctx.aircraftSelector.getValue();
            AirportInfo departureAirport = ctx.airportsRepository.findAirportByIcao(ctx.latestRouteDeparture);
            AirportInfo destinationAirport = ctx.airportsRepository.findAirportByIcao(ctx.latestRouteDestination);
            AirportWeather departureWeather = findRouteWeather(ctx.latestRouteDeparture);
            AirportWeather destinationWeather = findRouteWeather(ctx.latestRouteDestination);
            RouteAssessment routeAssessment = ctx.flightConditionEvaluator.assessRoute(
                    ctx.latestRouteResults,
                    ctx.latestRouteDeparture,
                    ctx.latestRouteDestination,
                    ctx.appSettings
            );

            RoutePlan routePlan = aircraftProfile == null
                    ? null
                    : ctx.flightPlanningService.planDirectRoute(departureAirport, destinationAirport, aircraftProfile, ctx.appSettings);
            TimedRouteAssessment timedRouteAssessment = null;
            LocalDateTime departureTimeUtc = getPlannedDepartureTimeUtc();
            if (routePlan != null && departureTimeUtc != null) {
                timedRouteAssessment = ctx.flightConditionEvaluator.assessTimedRoute(
                        departureWeather,
                        destinationWeather,
                        departureTimeUtc,
                        departureTimeUtc.plusMinutes((long) Math.round(routePlan.estimatedTimeHours() * 60)),
                        ctx.appSettings
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
                BriefingExportResult exportResult = ctx.briefingExportService.exportRouteBriefing(
                        routePlan,
                        aircraftProfile,
                        ctx.latestRouteResults,
                        routeAssessment,
                        timedRouteAssessment,
                        alerts,
                        ctx.latestAlternateOptions,
                        ctx.appSettings
                );
                routeStatusLabel.setText("Briefing exported: " + exportResult.textPath().getFileName() + " and " + exportResult.pdfPath().getFileName());
            } catch (Exception exception) {
                routeStatusLabel.setText("Could not export briefing: " + exception.getMessage());
            }
        });

        VBox plannerCard = ui.createPanel(
                "Direct Route Setup",
                "Enter a route, set departure time, then review the decision summary before diving into airport details.",
                buildRoutePlannerControls(planButton, exportButton)
        );
        VBox recentRoutesCard = ui.createPanel(
                "Recent Routes",
                "Reuse recent route checks with their saved UTC departure time and your preferred display format.",
                ctx.recentRoutesBox
        );

        HBox topRow = new HBox(16, plannerCard, recentRoutesCard);
        plannerCard.setMaxWidth(Double.MAX_VALUE);
        recentRoutesCard.setPrefWidth(340);
        HBox.setHgrow(plannerCard, Priority.ALWAYS);

        VBox content = new VBox(
                16,
                sectionTitle,
                sectionSubtitle,
                ui.buildSectionFlightStrip(
                        ui.createSectionStripCard("Dispatch", "Route decision board"),
                        ui.createSectionStripCard("Input Time", routeTimeModeLabel()),
                        ui.createSectionStripCard("Fuel Buffer", ui.formatOneDecimal(ctx.appSettings.taxiFuelGallons() + ctx.appSettings.climbFuelGallons()) + " gal")
                ),
                topRow,
                routeStatusLabel,
                ctx.routeResultsBox
        );
        content.setPadding(new Insets(24));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }

    public void rerenderRouteResults() {
        ctx.routeResultsBox.getChildren().clear();
        if (ctx.latestRouteResults.isEmpty() || ctx.latestRouteDeparture == null || ctx.latestRouteDestination == null) {
            return;
        }

        AircraftProfile aircraftProfile = ctx.aircraftSelector.getValue();
        RouteAssessment assessment = ctx.flightConditionEvaluator.assessRoute(
                ctx.latestRouteResults,
                ctx.latestRouteDeparture,
                ctx.latestRouteDestination,
                ctx.appSettings
        );
        VBox routeBanner = ui.createBanner(assessment.message(), assessment.level());

        LocalDateTime departureTimeUtc = null;
        TimedRouteAssessment timedRouteAssessment = null;
        if (aircraftProfile != null) {
            AirportInfo departureAirport = ctx.airportsRepository.findAirportByIcao(ctx.latestRouteDeparture);
            AirportInfo destinationAirport = ctx.airportsRepository.findAirportByIcao(ctx.latestRouteDestination);
            AirportWeather departureWeather = ctx.latestRouteResults.stream()
                    .filter(weather -> weather.metar().airportId().equalsIgnoreCase(ctx.latestRouteDeparture))
                    .findFirst()
                    .orElse(null);
            AirportWeather destinationWeather = ctx.latestRouteResults.stream()
                    .filter(weather -> weather.metar().airportId().equalsIgnoreCase(ctx.latestRouteDestination))
                    .findFirst()
                    .orElse(null);
            RoutePlan routePlan = ctx.flightPlanningService.planDirectRoute(departureAirport, destinationAirport, aircraftProfile, ctx.appSettings);
            if (routePlan != null) {
                departureTimeUtc = getPlannedDepartureTimeUtc();
                if (departureTimeUtc != null) {
                    timedRouteAssessment = ctx.flightConditionEvaluator.assessTimedRoute(
                            departureWeather,
                            destinationWeather,
                            departureTimeUtc,
                            departureTimeUtc.plusMinutes((long) Math.round(routePlan.estimatedTimeHours() * 60)),
                            ctx.appSettings
                    );
                    routeBanner = ui.createBanner(timedRouteAssessment.message(), timedRouteAssessment.level());
                }
                ctx.routeResultsBox.getChildren().add(buildRouteDecisionDashboard(
                        routeBanner,
                        routePlan,
                        aircraftProfile,
                        departureWeather,
                        destinationWeather,
                        assessment,
                        timedRouteAssessment
                ));
                ctx.routeResultsBox.getChildren().add(buildOperationalAlertsCard(
                        routePlan,
                        aircraftProfile,
                        departureWeather,
                        destinationWeather,
                        assessment,
                        timedRouteAssessment
                ));
            } else {
                ctx.routeResultsBox.getChildren().add(routeBanner);
            }
        } else {
            ctx.routeResultsBox.getChildren().add(routeBanner);
            ctx.routeResultsBox.getChildren().add(ui.createMutedLabel("Select an aircraft profile to unlock fuel and endurance planning."));
        }

        if (timedRouteAssessment != null && timedRouteAssessment.level() != RouteDecisionLevel.GO) {
            maybeLoadAlternateSuggestions();
        } else if (assessment.level() != RouteDecisionLevel.GO) {
            maybeLoadAlternateSuggestions();
        }

        if (ctx.alternateSuggestionsLoading) {
            ctx.routeResultsBox.getChildren().add(ui.createPanel(
                    "Alternate Airports",
                    "Searching nearby airports with better conditions and runway fit.",
                    ui.createMutedLabel("Loading alternates...")
            ));
        } else if (!ctx.latestAlternateOptions.isEmpty()) {
            ctx.routeResultsBox.getChildren().add(buildAlternatesCard());
        } else if (!ctx.alternateSuggestionsStatus.isBlank()) {
            ctx.routeResultsBox.getChildren().add(ui.createPanel(
                    "Alternate Airports",
                    "Nearby fallback options for the destination.",
                    ui.createMutedLabel(ctx.alternateSuggestionsStatus)
            ));
        }

        ctx.routeResultsBox.getChildren().add(buildRouteAirportDetailsCard());
    }

    VBox buildRouteDecisionDashboard(
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

        VBox timingCard = ui.createPanel(
                "Decision Snapshot",
                "Fast route-level view before reviewing airport detail sections.",
                routeBanner,
                buildDecisionSnapshotMetrics(routePlan, departureWeather, destinationWeather, assessment, timedRouteAssessment)
        );
        timingCard.setPrefWidth(340);

        VBox routeRibbon = ui.createInsetBanner(
                "Mission profile",
                routePlan.departureAirport().ident() + " -> " + routePlan.destinationAirport().ident() + "  |  " + aircraftProfile.name(),
                ui.decisionAccentColor(timedRouteAssessment == null ? assessment.level() : timedRouteAssessment.level())
        );

        HBox row = new HBox(16, summaryCard, timingCard);
        HBox.setHgrow(summaryCard, Priority.ALWAYS);

        VBox wrapper = new VBox(14, routeRibbon, row);
        return wrapper;
    }

    FlowPane buildDecisionSnapshotMetrics(
            RoutePlan routePlan,
            AirportWeather departureWeather,
            AirportWeather destinationWeather,
            RouteAssessment assessment,
            TimedRouteAssessment timedRouteAssessment
    ) {
        FlowPane metrics = new FlowPane();
        metrics.setHgap(10);
        metrics.setVgap(10);

        VfrAssessment departureCurrent = departureWeather == null ? null : ctx.flightConditionEvaluator.assessVfr(departureWeather.metar(), ctx.appSettings);
        VfrAssessment destinationCurrent = destinationWeather == null ? null : ctx.flightConditionEvaluator.assessVfr(destinationWeather.metar(), ctx.appSettings);

        metrics.getChildren().add(ui.createMetricCard(
                "Departure",
                departureCurrent == null ? "N/A" : departureCurrent.level().name(),
                ui.statusAccentColor(departureCurrent == null ? VfrStatusLevel.WARNING : departureCurrent.level())
        ));
        metrics.getChildren().add(ui.createMetricCard(
                "Destination",
                destinationCurrent == null ? "N/A" : destinationCurrent.level().name(),
                ui.statusAccentColor(destinationCurrent == null ? VfrStatusLevel.WARNING : destinationCurrent.level())
        ));

        if (timedRouteAssessment != null) {
            metrics.getChildren().add(ui.createMetricCard(
                    "Planned Window",
                    timedRouteAssessment.level().name(),
                    ui.decisionAccentColor(timedRouteAssessment.level())
            ));
        } else {
            metrics.getChildren().add(ui.createMetricCard(
                    "Route Outlook",
                    assessment.level().name(),
                    ui.decisionAccentColor(assessment.level())
            ));
        }

        metrics.getChildren().add(ui.createMetricCard(
                "Alternates",
                ctx.latestAlternateOptions.isEmpty() ? "None" : String.valueOf(ctx.latestAlternateOptions.size()),
                ctx.latestAlternateOptions.isEmpty() ? ctx.themePalette.textMuted() : ctx.themePalette.cautionOrange()
        ));

        return metrics;
    }

    VBox buildRouteAirportDetailsCard() {
        AirportWeather departureWeather = findRouteWeather(ctx.latestRouteDeparture);
        AirportWeather destinationWeather = findRouteWeather(ctx.latestRouteDestination);

        VBox departureCard = buildCompactRouteAirportCard("Departure Detail", departureWeather);
        VBox destinationCard = buildCompactRouteAirportCard("Destination Detail", destinationWeather);

        HBox row = new HBox(16, departureCard, destinationCard);
        HBox.setHgrow(departureCard, Priority.ALWAYS);
        HBox.setHgrow(destinationCard, Priority.ALWAYS);

        return ui.createPanel(
                "Airport Details",
                "Expanded weather, runway, and chart context for each end of the route.",
                row
        );
    }

    VBox buildCompactRouteAirportCard(String title, AirportWeather airportWeather) {
        if (airportWeather == null) {
            return ui.createPanel(title, "No airport data available.", ui.createMutedLabel("This side of the route could not be loaded."));
        }

        VBox card = new VBox(10);
        card.setPadding(new Insets(18));
        card.setStyle(ui.panelStyle(ctx.themePalette.surfaceBackground(), true));

        MetarData metar = airportWeather.metar();
        AircraftProfile selectedProfile = ctx.aircraftSelector.getValue();

        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.setStyle("-fx-text-fill: " + ctx.themePalette.unknownGray() + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-letter-spacing: 1.0px;");

        Label airportLabel = new Label(metar.airportId() + " - " + metar.airportName());
        airportLabel.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 20px; -fx-font-weight: bold;");
        airportLabel.setWrapText(true);

        HBox badges = new HBox(8, ui.createBadge(metar.flightCategory(), ui.categoryColor(metar.flightCategory())));
        if (selectedProfile != null) {
            badges.getChildren().add(ui.createBadge("XW " + ui.formatSpeed(selectedProfile.maxCrosswindKts()), ctx.themePalette.accentGold()));
        }

        FlowPane metrics = new FlowPane();
        metrics.setHgap(10);
        metrics.setVgap(10);
        metrics.getChildren().addAll(
                ui.createMetricCard("Wind", metar.windGust() > 0
                        ? ui.formatCompactWind(metar.windDir(), metar.windSpeed(), metar.windGust())
                        : ui.formatCompactWind(metar.windDir(), metar.windSpeed(), 0), ui.categoryColor(metar.flightCategory())),
                ui.createMetricCard("Vis", ui.formatVisibility(metar.visibilitySm()), ctx.themePalette.accentBlue()),
                ui.createMetricCard("Alt", ui.formatAltimeter(metar.altimeterInHg()), ctx.themePalette.accentGold()),
                ui.createMetricCard("Temp", ui.formatTemperature(metar.tempC()), ctx.themePalette.successGreen())
        );

        // Build sub-sections using WeatherView helpers via a temporary WeatherView
        WeatherView wv = new WeatherView(ctx, ui, openUrl);
        VBox sections = new VBox(
                10,
                wv.buildAirportBriefingSection(airportWeather.airportInfo(), metar),
                wv.buildTrendSection(airportWeather),
                wv.buildTafSection(airportWeather),
                wv.buildRunwaySection(metar, airportWeather.runways(), ui.categoryColor(metar.flightCategory()), selectedProfile)
        );

        VBox masthead = ui.createInsetBanner(
                title,
                metar.flightCategory() + "  |  " + ui.formatObservationTime(metar.observationTime()),
                ui.categoryColor(metar.flightCategory())
        );

        card.getChildren().addAll(titleLabel, airportLabel, badges, masthead, metrics, sections);
        return card;
    }

    VBox buildAlternatesCard() {
        VBox alternatesList = new VBox(10);
        AircraftProfile selectedAircraft = ctx.aircraftSelector.getValue();

        for (AlternateAirportOption option : ctx.latestAlternateOptions) {
            AirportWeather weather = option.airportWeather();
            String summary = weather.airportInfo() == null
                    ? weather.metar().airportId()
                    : weather.airportInfo().ident() + " - " + weather.airportInfo().name();

            Label title = new Label(summary);
            title.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 14px; -fx-font-weight: bold;");

            Label badges = new Label(
                    weather.metar().flightCategory() + "  |  "
                            + ui.formatDistance(option.distanceFromDestinationNm())
                            + " from destination  |  "
                            + option.vfrAssessment().level()
            );
            badges.setStyle("-fx-text-fill: " + ctx.themePalette.textMuted() + "; -fx-font-size: 12px;");

            String runwayLine = "No runway suitability data available.";
            List<RunwayAnalysis> runwayAnalysis = ctx.runwayAnalysisService.analyze(weather.metar(), weather.runways(), selectedAircraft);
            if (!runwayAnalysis.isEmpty()) {
                RunwayAnalysis best = runwayAnalysis.getFirst();
                runwayLine = "Best runway " + best.runway().ident()
                        + " with crosswind "
                        + ui.formatSpeed(Math.abs(best.components().crosswindKts()));
                if (best.exceedsAircraftLimit() && selectedAircraft != null) {
                    runwayLine += " (above selected aircraft limit)";
                }
            }

            Label summaryLabel = ui.createMutedLabel(option.summary());
            Label runwayLabel = ui.createMutedLabel(runwayLine);

            VBox optionCard = new VBox(4, title, badges, summaryLabel, runwayLabel);
            optionCard.setPadding(new Insets(12));
            optionCard.setStyle(
                    "-fx-background-color: " + ctx.themePalette.insetBackground() + "; -fx-border-color: " + ctx.themePalette.insetBorder() + "; " +
                            "-fx-background-radius: 14; -fx-border-radius: 14;"
            );
            alternatesList.getChildren().add(optionCard);
        }

        return ui.createPanel(
                "Alternate Airports",
                "Nearby options ranked by weather and runway fit.",
                alternatesList
        );
    }

    VBox buildOperationalAlertsCard(
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
            return ui.createPanel(
                    "Operational Alerts",
                    "CloudDeck summarizes route-specific risks here.",
                    ui.createMutedLabel("No additional operational alerts for the current assumptions.")
            );
        }

        VBox alertList = new VBox(10);
        for (OperationalAlert alert : alerts) {
            Label title = new Label(alert.title());
            title.setWrapText(true);
            title.setStyle(switch (alert.level()) {
                case WARNING -> "-fx-text-fill: " + ctx.themePalette.warningRed() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
                case CAUTION -> "-fx-text-fill: " + ctx.themePalette.cautionOrange() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
                case VFR -> "-fx-text-fill: " + ctx.themePalette.successGreen() + "; -fx-font-size: 13px; -fx-font-weight: bold;";
            });

            Label detail = ui.createMutedLabel(alert.detail());
            VBox item = new VBox(4, title, detail);
            item.setPadding(new Insets(12));
            item.setStyle(
                    "-fx-background-color: " + ctx.themePalette.metricBackground() + "; -fx-border-color: " + ctx.themePalette.metricBorder() + "; " +
                            "-fx-background-radius: 14; -fx-border-radius: 14;"
            );
            alertList.getChildren().add(item);
        }

        return ui.createPanel(
                "Operational Alerts",
                "Centralized warnings for weather, runway fit, fuel, and daylight timing.",
                alertList
        );
    }

    List<OperationalAlert> collectRouteOperationalAlerts(
            RoutePlan routePlan,
            AircraftProfile aircraftProfile,
            AirportWeather departureWeather,
            AirportWeather destinationWeather,
            RouteAssessment routeAssessment,
            TimedRouteAssessment timedRouteAssessment
    ) {
        List<RunwayAnalysis> departureRunways = departureWeather == null
                ? List.of()
                : ctx.runwayAnalysisService.analyze(departureWeather.metar(), departureWeather.runways(), aircraftProfile);
        List<RunwayAnalysis> destinationRunways = destinationWeather == null
                ? List.of()
                : ctx.runwayAnalysisService.analyze(destinationWeather.metar(), destinationWeather.runways(), aircraftProfile);

        boolean departureInDaylight = true;
        boolean arrivalInDaylight = true;
        if (routePlan != null) {
            LocalDateTime departureTimeUtc = getPlannedDepartureTimeUtc();
            LocalDateTime arrivalTimeUtc = departureTimeUtc == null
                    ? null
                    : departureTimeUtc.plusMinutes((long) Math.round(routePlan.estimatedTimeHours() * 60));
            if (departureTimeUtc != null) {
                SolarTimes departureSolar = ctx.solarCalculatorService.calculate(routePlan.departureAirport(), departureTimeUtc.toLocalDate());
                if (departureSolar != null) {
                    departureInDaylight = ctx.solarCalculatorService.isDaylight(departureSolar, departureTimeUtc);
                }
            }
            if (arrivalTimeUtc != null) {
                SolarTimes destinationSolar = ctx.solarCalculatorService.calculate(routePlan.destinationAirport(), arrivalTimeUtc.toLocalDate());
                if (destinationSolar != null) {
                    arrivalInDaylight = ctx.solarCalculatorService.isDaylight(destinationSolar, arrivalTimeUtc);
                }
            }
        }

        return ctx.operationalAlertService.buildRouteAlerts(
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
                !ctx.latestAlternateOptions.isEmpty(),
                ctx.appSettings
        );
    }

    VBox buildRouteSummaryCard(RoutePlan routePlan, AircraftProfile aircraftProfile) {
        FlowPane metrics = new FlowPane();
        metrics.setHgap(12);
        metrics.setVgap(12);
        metrics.getChildren().addAll(
                ui.createMetricCard("Distance", ui.formatDistance(routePlan.distanceNm()), ctx.themePalette.accentBlue()),
                ui.createMetricCard("Groundspeed", ui.formatSpeed(routePlan.groundspeedKts()), ctx.themePalette.cautionOrange()),
                ui.createMetricCard("ETE", ui.formatDuration(routePlan.estimatedTimeHours()), ctx.themePalette.successGreen()),
                ui.createMetricCard("Trip Fuel", ui.formatOneDecimal(routePlan.tripFuelGallons()) + " gal", ctx.themePalette.accentGold()),
                ui.createMetricCard("Reserve Left", ui.formatOneDecimal(routePlan.reserveRemainingGallons()) + " gal",
                        routePlan.reserveSatisfied() ? ctx.themePalette.successGreen() : ctx.themePalette.warningRed())
        );

        AirportInfo departureAirport = routePlan.departureAirport();
        AirportInfo destinationAirport = routePlan.destinationAirport();
        AirportWeather departureWeather = ctx.latestRouteResults.stream()
                .filter(weather -> weather.metar().airportId().equalsIgnoreCase(departureAirport.ident()))
                .findFirst()
                .orElse(null);
        AirportWeather destinationWeather = ctx.latestRouteResults.stream()
                .filter(weather -> weather.metar().airportId().equalsIgnoreCase(destinationAirport.ident()))
                .findFirst()
                .orElse(null);

        DensityAltitudeAssessment departureDensity = departureWeather == null ? null
                : ctx.densityAltitudeService.assess(departureAirport, departureWeather.metar(), ctx.appSettings);
        DensityAltitudeAssessment destinationDensity = destinationWeather == null ? null
                : ctx.densityAltitudeService.assess(destinationAirport, destinationWeather.metar(), ctx.appSettings);

        Label note = routePlan.reserveSatisfied()
                ? ui.createMutedLabel(aircraftProfile.name() + " reserve target is satisfied for a direct route.")
                : ui.createMutedLabel("Reserve target is not met for " + aircraftProfile.name() + ". Consider fuel, a stop, or a different aircraft.");
        note.setStyle("-fx-text-fill: " + (routePlan.reserveSatisfied() ? ctx.themePalette.textMuted() : ctx.themePalette.warningRed()) + "; -fx-font-size: 12px;");

        LocalDateTime departureTimeUtc = getPlannedDepartureTimeUtc();
        LocalDateTime arrivalTimeUtc = departureTimeUtc == null
                ? null
                : departureTimeUtc.plusMinutes((long) Math.round(routePlan.estimatedTimeHours() * 60));
        if (departureTimeUtc != null && arrivalTimeUtc != null) {
            metrics.getChildren().add(ui.createMetricCard("Departure UTC", ui.formatUtcDateTime(departureTimeUtc), ctx.themePalette.accentGold()));
            metrics.getChildren().add(ui.createMetricCard("Departure Local", ui.formatLocalDateTime(departureTimeUtc), ctx.themePalette.accentBlue()));
            metrics.getChildren().add(ui.createMetricCard("Arrival UTC", ui.formatUtcDateTime(arrivalTimeUtc), ctx.themePalette.accentGold()));
            metrics.getChildren().add(ui.createMetricCard("Arrival Local", ui.formatLocalDateTime(arrivalTimeUtc), ctx.themePalette.accentBlue()));
        }

        VBox densityBox = new VBox(4);
        densityBox.getChildren().add(ui.createSubsectionTitle("Performance Snapshot"));
        if (departureDensity != null) {
            densityBox.getChildren().add(ui.createMutedLabel(
                    departureAirport.ident() + " density altitude: " + departureDensity.densityAltitudeFt() + " ft"
            ));
        }
        if (destinationDensity != null) {
            densityBox.getChildren().add(ui.createMutedLabel(
                    destinationAirport.ident() + " density altitude: " + destinationDensity.densityAltitudeFt() + " ft"
            ));
        }
        densityBox.getChildren().add(ui.createMutedLabel(
                "Assumptions: taxi " + ui.formatOneDecimal(routePlan.taxiFuelGallons()) + " gal, climb "
                        + ui.formatOneDecimal(routePlan.climbFuelGallons()) + " gal, airborne burn "
                        + ui.formatOneDecimal(routePlan.airborneFuelGallons()) + " gal."
        ));
        densityBox.getChildren().add(ui.createMutedLabel(
                "Groundspeed uses aircraft cruise " + ui.formatSpeed(aircraftProfile.cruiseSpeedKts()) + " with "
                        + ui.formatSignedSpeed(ctx.appSettings.groundspeedAdjustmentKts()) + " adjustment."
        ));

        if (departureTimeUtc != null && arrivalTimeUtc != null) {
            SolarTimes departureSolar = ctx.solarCalculatorService.calculate(departureAirport, departureTimeUtc.toLocalDate());
            SolarTimes destinationSolar = ctx.solarCalculatorService.calculate(destinationAirport, arrivalTimeUtc.toLocalDate());

            VBox solarBox = new VBox(4);
            solarBox.getChildren().add(ui.createSubsectionTitle("Daylight Snapshot"));
            if (departureSolar != null) {
                solarBox.getChildren().add(ui.createMutedLabel(ui.formatSolarPlanningLine(
                        departureAirport.ident(),
                        "Departure",
                        departureSolar,
                        departureTimeUtc
                )));
            }
            if (destinationSolar != null) {
                solarBox.getChildren().add(ui.createMutedLabel(ui.formatSolarPlanningLine(
                        destinationAirport.ident(),
                        "Arrival",
                        destinationSolar,
                        arrivalTimeUtc
                )));
            }

            if (solarBox.getChildren().size() == 1) {
                solarBox.getChildren().add(ui.createMutedLabel("Sunrise and sunset data unavailable for one or more airports."));
            }

            densityBox.getChildren().add(new Separator());
            densityBox.getChildren().add(solarBox);
        }

        return ui.createPanel(
                "Aircraft Planning Summary",
                routePlan.departureAirport().ident() + " to " + routePlan.destinationAirport().ident() + " using " + aircraftProfile.name(),
                metrics,
                densityBox,
                note
        );
    }

    void refreshRecentRoutes() {
        ctx.recentRoutesBox.getChildren().clear();
        List<RecentRouteEntry> recentRoutes = ctx.routeHistoryRepository.loadRecentRoutes();
        if (recentRoutes.isEmpty()) {
            ctx.recentRoutesBox.getChildren().add(ui.createMutedLabel("No recent routes yet. Analyze a route to save it here."));
            return;
        }

        for (RecentRouteEntry entry : recentRoutes) {
            Label routeLabel = new Label(entry.departureAirport() + " -> " + entry.destinationAirport());
            routeLabel.setStyle("-fx-text-fill: " + ctx.themePalette.textPrimary() + "; -fx-font-size: 13px; -fx-font-weight: bold;");
            routeLabel.setWrapText(true);
            routeLabel.setMaxWidth(Double.MAX_VALUE);

            String timestamp = "Saved departure " + entry.plannedDepartureUtc().format(UiHelper.ROUTE_TIME_FORMATTER)
                    + " UTC  |  Local " + ui.formatLocalDateTime(entry.plannedDepartureUtc())
                    + "  |  Last used " + ui.formatDateTime(entry.lastUsedUtc());
            Label metaLabel = ui.createMutedLabel(timestamp);

            Button useButton = ui.createSecondaryButton("Run Saved Route");
            useButton.setOnAction(event -> {
                populateRouteInputs(entry.departureAirport(), entry.destinationAirport(), entry.plannedDepartureUtc());
                analyzeRoute(entry.departureAirport(), entry.destinationAirport(), entry.plannedDepartureUtc());
            });

            Button editTimeButton = ui.createGhostButton("Edit Time");
            editTimeButton.setOnAction(event -> {
                populateRouteInputs(entry.departureAirport(), entry.destinationAirport(), entry.plannedDepartureUtc());
                if (routeDepartureTimeInput != null) {
                    routeDepartureTimeInput.requestFocus();
                    routeDepartureTimeInput.selectAll();
                }
                routeStatusLabel.setText("Route loaded. Adjust the " + routeTimeModeLabel() + " departure time, then click Analyze Route.");
            });

            HBox actionRow = new HBox(10, useButton, editTimeButton);
            actionRow.setAlignment(Pos.CENTER_LEFT);

            VBox routeCard = new VBox(8, routeLabel, metaLabel, actionRow);
            routeCard.setPadding(new Insets(12));
            routeCard.setStyle(
                    "-fx-background-color: " + ctx.themePalette.metricBackground() + "; -fx-border-color: " + ctx.themePalette.metricBorder() + "; " +
                            "-fx-background-radius: 14; -fx-border-radius: 14;"
            );
            ctx.recentRoutesBox.getChildren().add(routeCard);
        }
    }

    void populateRouteInputs(String departure, String destination, LocalDateTime plannedDepartureUtc) {
        ctx.latestRouteDeparture = departure;
        ctx.latestRouteDestination = destination;
        if (routeDepartureInput != null) {
            routeDepartureInput.setText(departure);
        }
        if (routeDestinationInput != null) {
            routeDestinationInput.setText(destination);
        }
        if (routeDepartureTimeInput != null && plannedDepartureUtc != null) {
            routeDepartureTimeInput.setText(formatRouteInputValue(plannedDepartureUtc, currentRouteTimeMode()));
        }
        updateRouteTimeHelper();
    }

    VBox buildRoutePlannerControls(Button planButton, Button exportButton) {
        VBox fields = new VBox(12);

        VBox departureBox = new VBox(6, ui.formLabel("Departure"), routeDepartureInput);
        VBox destinationBox = new VBox(6, ui.formLabel("Destination"), routeDestinationInput);
        VBox timeBox = new VBox(6, ui.formLabel("Departure Time"), routeDepartureTimeInput, routeTimeHelperLabel);
        VBox timeModeBox = new VBox(6, ui.formLabel("Time Input"), routeTimeModeBox);

        HBox firstRow = new HBox(12, departureBox, destinationBox, timeBox, timeModeBox);
        HBox.setHgrow(departureBox, Priority.ALWAYS);
        HBox.setHgrow(destinationBox, Priority.ALWAYS);
        HBox.setHgrow(timeBox, Priority.ALWAYS);
        HBox.setHgrow(timeModeBox, Priority.NEVER);

        Label plannerHint = ui.createMutedLabel(
                "Uses the selected aircraft profile plus route assumptions from Settings. Enter UTC or local time, then CloudDeck converts it internally to UTC."
        );
        HBox actionRow = new HBox(10, planButton, exportButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        fields.getChildren().addAll(firstRow, plannerHint, actionRow);
        return fields;
    }

    void analyzeRouteFromInputs() {
        String departure = routeDepartureInput.getText().trim().toUpperCase();
        String destination = routeDestinationInput.getText().trim().toUpperCase();

        if (departure.isEmpty() || destination.isEmpty()) {
            routeStatusLabel.setText("Please enter both a departure and destination.");
            return;
        }

        LocalDateTime plannedDepartureUtc;
        plannedDepartureUtc = getPlannedDepartureTimeUtc();
        if (plannedDepartureUtc == null) {
            routeStatusLabel.setText("Use " + routeTimeModeLabel() + " departure time format yyyy-MM-dd HH:mm.");
            return;
        }

        analyzeRoute(departure, destination, plannedDepartureUtc);
    }

    void analyzeRoute(String departure, String destination, LocalDateTime plannedDepartureUtc) {
        if (plannedDepartureUtc == null) {
            routeStatusLabel.setText("Use " + routeTimeModeLabel() + " departure time format yyyy-MM-dd HH:mm.");
            return;
        }

        populateRouteInputs(departure, destination, plannedDepartureUtc);

        ctx.routeResultsBox.getChildren().setAll(routeStatusLabel);
        routeStatusLabel.setText("Fetching route weather and forecast data...");

        ctx.runAsync(
                () -> ctx.weatherService.fetchAirportWeather(departure + "," + destination),
                weather -> {
                    ctx.latestRouteResults = weather;
                    ctx.resetLazyHistoryStatuses(weather);
                    ctx.routeHistoryRepository.saveRecentRoute(departure, destination, plannedDepartureUtc);
                    ctx.invalidateAlternateSuggestions();
                    refreshRecentRoutes();
                    routeStatusLabel.setText("");
                    rerenderRouteResults();
                },
                throwable -> routeStatusLabel.setText("Error: " + throwable.getMessage())
        );
    }

    void maybeLoadAlternateSuggestions() {
        if (ctx.alternateSuggestionsLoading || !ctx.latestAlternateOptions.isEmpty() || !ctx.alternateSuggestionsStatus.isBlank()) {
            return;
        }

        AircraftProfile selectedAircraft = ctx.aircraftSelector.getValue();
        ctx.alternateSuggestionsLoading = true;
        ctx.alternateSuggestionsStatus = "";

        ctx.runAsync(
                () -> ctx.alternateAirportService.suggestAlternates(
                        ctx.latestRouteDeparture,
                        ctx.latestRouteDestination,
                        selectedAircraft,
                        ctx.appSettings,
                        4
                ),
                alternates -> {
                    ctx.latestAlternateOptions = alternates;
                    ctx.alternateSuggestionsLoading = false;
                    ctx.alternateSuggestionsStatus = alternates.isEmpty()
                            ? "No suitable nearby alternates were found within the search radius."
                            : "";
                    rerenderRouteResults();
                },
                throwable -> {
                    ctx.latestAlternateOptions = List.of();
                    ctx.alternateSuggestionsLoading = false;
                    ctx.alternateSuggestionsStatus = "Could not load alternate airports: " + throwable.getMessage();
                    rerenderRouteResults();
                }
        );
    }

    AirportWeather findRouteWeather(String airportId) {
        return ctx.latestRouteResults.stream()
                .filter(weather -> weather.metar().airportId().equalsIgnoreCase(airportId))
                .findFirst()
                .orElse(null);
    }

    LocalDateTime getPlannedDepartureTimeUtc() {
        if (routeDepartureTimeInput == null || routeDepartureTimeInput.getText().isBlank()) {
            return null;
        }
        return parseRouteTimeInput(routeDepartureTimeInput.getText(), currentRouteTimeMode());
    }

    LocalDateTime parseRouteTimeInput(String rawValue, TimeDisplayMode inputMode) {
        if (rawValue == null || rawValue.isBlank() || inputMode == null) {
            return null;
        }

        try {
            LocalDateTime parsedTime = LocalDateTime.parse(rawValue.trim(), UiHelper.ROUTE_TIME_FORMATTER);
            if (inputMode == TimeDisplayMode.UTC) {
                return parsedTime;
            }
            return parsedTime.atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    String formatRouteInputValue(LocalDateTime timeUtc, TimeDisplayMode inputMode) {
        if (timeUtc == null) {
            return "";
        }
        if (inputMode == TimeDisplayMode.LOCAL) {
            return timeUtc.atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(UiHelper.ROUTE_TIME_FORMATTER);
        }
        return timeUtc.format(UiHelper.ROUTE_TIME_FORMATTER);
    }

    void updateRouteTimeHelper() {
        if (routeTimeHelperLabel == null) {
            return;
        }

        LocalDateTime departureTimeUtc = getPlannedDepartureTimeUtc();
        if (departureTimeUtc == null) {
            routeTimeHelperLabel.setText("Enter time as yyyy-MM-dd HH:mm in " + routeTimeModeLabel() + ".");
            return;
        }

        routeTimeHelperLabel.setText(
                "UTC: " + ui.formatUtcDateTime(departureTimeUtc) + "  |  Local: " + ui.formatLocalDateTime(departureTimeUtc)
        );
    }

    TimeDisplayMode currentRouteTimeMode() {
        return routeTimeModeBox == null || routeTimeModeBox.getValue() == null
                ? TimeDisplayMode.UTC
                : routeTimeModeBox.getValue();
    }

    String routeTimeModeLabel() {
        return currentRouteTimeMode() == TimeDisplayMode.LOCAL ? "Local" : "UTC";
    }
}
