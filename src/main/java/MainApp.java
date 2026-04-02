/* Designed by: Kyle Barnes
   JavaFX GUI entry point for CloudDeck — card-based layout with color-coded
   flight categories, VFR minimums checker, and auto-sorted runway analysis
 */

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainApp extends Application {

    //===== INSTANCES =====
    private final MetarFetcher fetcher = new MetarFetcher();
    private final MetarParser parser = new MetarParser();
    private final RunwayFetcher runwayFetcher = new RunwayFetcher();
    private final VBox favoritesBar = new VBox(4);

    //===== START =====
    @Override
    public void start(Stage stage) {

        //header
        Label title = new Label("CloudDeck");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        title.setTextFill(Color.web("#4ea8de"));

        Label subtitle = new Label("Aviation Weather Tool");
        subtitle.setFont(Font.font("Arial", 14));
        subtitle.setTextFill(Color.web("#888888"));

        //input field and fetch button
        TextField airportInput = new TextField();
        airportInput.setPromptText("Enter ICAO ID(s) ex: KDFW,KHOU,KLFK");
        airportInput.setStyle(
                "-fx-background-color: #2a2a2a; -fx-text-fill: white; " +
                        "-fx-prompt-text-fill: #666666; -fx-font-size: 14px; -fx-padding: 10px;"
        );
        airportInput.setMaxWidth(400);

        Button fetchButton = new Button("Get Weather");
        fetchButton.setStyle(
                "-fx-background-color: #4ea8de; -fx-text-fill: white; " +
                        "-fx-font-size: 14px; -fx-padding: 10px 20px; -fx-cursor: hand;"
        );

        //scrollable card container
        VBox cardsContainer = new VBox(12);
        cardsContainer.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(cardsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #121212; -fx-background: #121212;");
        scrollPane.setPrefHeight(450);

        //status label shown while loading
        Label statusLabel = new Label("");
        statusLabel.setTextFill(Color.web("#888888"));
        statusLabel.setFont(Font.font("Arial", 13));

        //===== BUTTON ACTION =====
        fetchButton.setOnAction(e -> {
            String input = airportInput.getText().trim();

            if (input.isEmpty()) {
                statusLabel.setText("Please enter at least one ICAO airport ID.");
                return;
            }

            cardsContainer.getChildren().clear();
            statusLabel.setText("Fetching weather and runway data...");

            //run API calls on background thread so UI doesn't freeze
            new Thread(() -> {
                try {
                    JSONArray results = fetcher.fetchRaw(input);
                    List<MetarData> metarList = new ArrayList<>();

                    for (int i = 0; i < results.length(); i++) {
                        metarList.add(parser.parse(results.getJSONObject(i)));
                    }

                    //sort results to match user input order
                    List<String> inputOrder = Arrays.asList(input.toUpperCase().split(","));
                    metarList.sort((a, b) ->
                            inputOrder.indexOf(a.getAirportId()) - inputOrder.indexOf(b.getAirportId()));

                    //build a card for each airport
                    List<VBox> cards = new ArrayList<>();
                    for (MetarData metar : metarList) {
                        List<Runway> runways = runwayFetcher.fetchRunways(metar.getAirportId());
                        cards.add(buildAirportCard(metar, runways));
                    }

                    //push cards to UI on JavaFX thread
                    Platform.runLater(() -> {
                        cardsContainer.getChildren().addAll(cards);
                        statusLabel.setText("");
                        refreshFavoritesBar(airportInput);
                    });
                }
                catch (Exception ex) {
                    Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
                }
            }).start();
        });

        //===== LAYOUT =====
        HBox inputRow = new HBox(10, airportInput, fetchButton);
        inputRow.setAlignment(Pos.CENTER);

        //load favorites on startup
        refreshFavoritesBar(airportInput);

        VBox weatherPane = new VBox(10, title, subtitle, inputRow, favoritesBar, statusLabel, scrollPane);
        weatherPane.setPadding(new Insets(20));
        weatherPane.setAlignment(Pos.TOP_CENTER);
        weatherPane.setStyle("-fx-background-color: #121212;");

        //route planner pane
        ScrollPane routeScroll = new ScrollPane(buildRoutePlanner());
        routeScroll.setFitToWidth(true);
        routeScroll.setStyle("-fx-background-color: #121212; -fx-background: #121212;");

        //tab pane
        Tab weatherTab = new Tab("Weather", weatherPane);
        weatherTab.setClosable(false);

        Tab routeTab = new Tab("Route Planner", routeScroll);
        routeTab.setClosable(false);

        TabPane tabPane = new TabPane(weatherTab, routeTab);
        tabPane.setStyle("-fx-background-color: #121212;");

        //===== SCENE =====
        Scene scene = new Scene(tabPane, 800, 660);        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setTitle("CloudDeck");
        stage.setScene(scene);
        stage.show();
    }

    //===== FAVORITES BAR =====
    private void refreshFavoritesBar(TextField airportInput) {
        favoritesBar.getChildren().clear();
        List<String> favorites = FavoritesManager.loadFavorites();

        if (favorites.isEmpty()) return;

        Label favTitle = new Label("Favorites:");
        favTitle.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");

        HBox chips = new HBox(6);
        chips.setAlignment(Pos.CENTER_LEFT);

        for (String icao : favorites) {
            Button chip = new Button(icao);
            chip.setStyle(
                    "-fx-background-color: #2a2a2a; -fx-text-fill: #4ea8de; " +
                            "-fx-font-size: 12px; -fx-padding: 3px 10px; " +
                            "-fx-background-radius: 12; -fx-cursor: hand;"
            );
            chip.setOnAction(e -> airportInput.setText(icao));

            Button remove = new Button("✕");
            remove.setStyle(
                    "-fx-background-color: transparent; -fx-text-fill: #666666; " +
                            "-fx-font-size: 11px; -fx-padding: 3px 6px; -fx-cursor: hand;"
            );
            remove.setOnAction(e -> {
                FavoritesManager.removeFavorite(icao);
                refreshFavoritesBar(airportInput);
            });

            HBox chipRow = new HBox(2, chip, remove);
            chipRow.setAlignment(Pos.CENTER_LEFT);
            chips.getChildren().add(chipRow);
        }

        favoritesBar.getChildren().addAll(favTitle, chips);
    }

    //===== AIRPORT CARD BUILDER =====
    private VBox buildAirportCard(MetarData metar, List<Runway> runways) {

        //card container
        VBox card = new VBox(8);
        card.setPadding(new Insets(15));
        card.setStyle(
                "-fx-background-color: #1e1e1e; -fx-background-radius: 8; " +
                        "-fx-border-color: #333333; -fx-border-radius: 8; -fx-border-width: 1;"
        );

        //===== FLIGHT CATEGORY COLOR =====
        String category = metar.getFlightCategory();
        Color categoryColor = switch (category) {
            case "VFR"  -> Color.web("#00cc44");  //green
            case "MVFR" -> Color.web("#4ea8de");  //blue
            case "IFR"  -> Color.web("#ff4444");  //red
            case "LIFR" -> Color.web("#cc44cc");  //purple
            default     -> Color.web("#888888");  //grey
        };

        //convert Color to hex string for use in inline CSS
        String colorHex = String.format("#%02X%02X%02X",
                (int)(categoryColor.getRed() * 255),
                (int)(categoryColor.getGreen() * 255),
                (int)(categoryColor.getBlue() * 255));

        //===== HEADER ROW =====
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label airportLabel = new Label(metar.getAirportId() + " — ");
        airportLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        Label categoryLabel = new Label(category);
        categoryLabel.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-size: 18px; -fx-font-weight: bold;");

        HBox headerRow = new HBox(8, dot, airportLabel, categoryLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        //favorite toggle button
        boolean isFav = FavoritesManager.isFavorite(metar.getAirportId());
        Button favBtn = new Button(isFav ? "★ Saved" : "☆ Save");
        favBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #ffaa00; " +
                        "-fx-font-size: 12px; -fx-padding: 3px 8px; -fx-cursor: hand;"
        );
        favBtn.setOnAction(e -> {
            if (FavoritesManager.isFavorite(metar.getAirportId())) {
                FavoritesManager.removeFavorite(metar.getAirportId());
                favBtn.setText("☆ Save");
            }
            else {
                FavoritesManager.addFavorite(metar.getAirportId());
                favBtn.setText("★ Saved");
            }
        });

        HBox headerWithFav = new HBox(8, headerRow, favBtn);
        headerWithFav.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerRow, Priority.ALWAYS);

        //airport full name shown below the ICAO ID (if available)
        Label nameLabel = makeInfoLabel(metar.getAirportName());
        nameLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 15px; -fx-font-style: bold;");

        //===== WEATHER LABELS =====
        //wind — include gusts if present
        String windStr = metar.getWindGust() > 0
                ? String.format("Wind: %03d° at %d kts gusting %d kts",
                metar.getWindDir(), metar.getWindSpeed(), metar.getWindGust())
                : String.format("Wind: %03d° at %d kts",
                metar.getWindDir(), metar.getWindSpeed());
        Label windLabel = makeInfoLabel(windStr);

        //altimeter and clouds
        Label altLabel = makeInfoLabel(String.format(
                "Altimeter: %.2f inHg  |  Clouds: %s",
                metar.getAltimeter(), metar.getCloudLayers()));

        //temperature (C and F) and visibility
        float tempF = (metar.getTempC() * 9 / 5) + 32;
        Label weatherLabel = makeInfoLabel(String.format(
                "Temp: %.1f°F (%.1f°C)  |  Visibility: %.1f SM",
                tempF, metar.getTempC(), metar.getVisibSM()));

        //===== VFR MINIMUMS =====
        String vfrStatus = checkVfrMinimums(metar);
        Label vfrLabel = new Label(vfrStatus);
        vfrLabel.setWrapText(true);
        if (vfrStatus.contains("WARNING")) {
            vfrLabel.setStyle("-fx-text-fill: #ff4444; -fx-font-size: 13px; -fx-font-weight: bold;");
        }
        else if (vfrStatus.contains("CAUTION")) {
            vfrLabel.setStyle("-fx-text-fill: #ffaa00; -fx-font-size: 13px; -fx-font-weight: bold;");
        }
        else {
            vfrLabel.setStyle("-fx-text-fill: #00cc44; -fx-font-size: 13px;");
        }

        //observation time
        Label timeLabel = makeInfoLabel("Observation: " + metar.getObservationTime()
                .replace("T", " ").replace(".000Z", "Z"));

        //raw METAR string
        Label rawLabel = makeInfoLabel("Raw: " + metar.getRawOb());
        rawLabel.setStyle("-fx-text-fill: #666666; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        rawLabel.setWrapText(true);

        //===== RUNWAY SECTION =====
        VBox runwaySection = buildRunwaySection(metar, runways, colorHex);

        //assemble card
        card.getChildren().addAll(
                headerWithFav, nameLabel, windLabel, altLabel, weatherLabel,
                vfrLabel, timeLabel, rawLabel, runwaySection
        );
        return card;
    }

    //===== ROUTE PLANNER =====
//builds a route planner section with departure and destination inputs
    public VBox buildRoutePlanner() {
        VBox routePane = new VBox(12);
        routePane.setPadding(new Insets(10));

        //===== HEADER =====
        Label routeTitle = new Label("Route Planner");
        routeTitle.setStyle("-fx-text-fill: #4ea8de; -fx-font-size: 18px; -fx-font-weight: bold;");

        Label routeSubtitle = makeInfoLabel("Enter departure and destination to get a VFR go/no-go recommendation.");
        routeSubtitle.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");

        //===== INPUT ROW =====
        TextField depInput = new TextField();
        depInput.setPromptText("Departure ex: KLFK");
        depInput.setMaxWidth(180);
        depInput.setStyle(
                "-fx-background-color: #2a2a2a; -fx-text-fill: white; " +
                        "-fx-prompt-text-fill: #666666; -fx-font-size: 13px; -fx-padding: 8px;"
        );

        Label arrowLabel = new Label("→");
        arrowLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 18px;");

        TextField destInput = new TextField();
        destInput.setPromptText("Destination ex: KDFW");
        destInput.setMaxWidth(180);
        destInput.setStyle(
                "-fx-background-color: #2a2a2a; -fx-text-fill: white; " +
                        "-fx-prompt-text-fill: #666666; -fx-font-size: 13px; -fx-padding: 8px;"
        );

        Button planButton = new Button("Check Route");
        planButton.setStyle(
                "-fx-background-color: #4ea8de; -fx-text-fill: white; " +
                        "-fx-font-size: 13px; -fx-padding: 8px 16px; -fx-cursor: hand;"
        );

        HBox inputRow = new HBox(10, depInput, arrowLabel, destInput, planButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        //===== RESULTS =====
        VBox resultsBox = new VBox(10);
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 13px;");
        resultsBox.getChildren().add(statusLabel);

        //===== BUTTON ACTION =====
        planButton.setOnAction(e -> {
            String dep = depInput.getText().trim().toUpperCase();
            String dest = destInput.getText().trim().toUpperCase();

            if (dep.isEmpty() || dest.isEmpty()) {
                statusLabel.setText("Please enter both a departure and destination.");
                return;
            }

            statusLabel.setText("Fetching route weather data...");
            resultsBox.getChildren().clear();
            resultsBox.getChildren().add(statusLabel);

            new Thread(() -> {
                try {
                    //fetch both METARs at once
                    JSONArray results = fetcher.fetchRaw(dep + "," + dest);
                    List<MetarData> metarList = new ArrayList<>();

                    for (int i = 0; i < results.length(); i++) {
                        metarList.add(parser.parse(results.getJSONObject(i)));
                    }

                    //sort so departure is always first
                    metarList.sort((a, b) -> {
                        if (a.getAirportId().equals(dep)) return -1;
                        if (b.getAirportId().equals(dep)) return 1;
                        return 0;
                    });

                    //build recommendation
                    String recommendation = buildRouteRecommendation(metarList, dep, dest);

                    //build cards for both airports
                    List<VBox> cards = new ArrayList<>();
                    for (MetarData metar : metarList) {
                        List<Runway> runways = runwayFetcher.fetchRunways(metar.getAirportId());
                        cards.add(buildAirportCard(metar, runways));
                    }

                    Platform.runLater(() -> {
                        resultsBox.getChildren().clear();

                        //recommendation banner
                        Label recLabel = new Label(recommendation);
                        recLabel.setWrapText(true);
                        recLabel.setStyle(recommendation.contains("GO —")
                                ? "-fx-text-fill: #00cc44; -fx-font-size: 15px; -fx-font-weight: bold;"
                                : recommendation.contains("NO-GO")
                                ? "-fx-text-fill: #ff4444; -fx-font-size: 15px; -fx-font-weight: bold;"
                                : "-fx-text-fill: #ffaa00; -fx-font-size: 15px; -fx-font-weight: bold;");

                        resultsBox.getChildren().add(recLabel);
                        resultsBox.getChildren().addAll(cards);
                    });
                }
                catch (Exception ex) {
                    Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
                }
            }).start();
        });

        //assemble route pane
        routePane.getChildren().addAll(routeTitle, routeSubtitle, inputRow, resultsBox);
        return routePane;
    }

    //===== ROUTE RECOMMENDATION BUILDER =====
    private String buildRouteRecommendation(List<MetarData> metarList, String dep, String dest) {
        boolean depOk = true;
        boolean destOk = true;
        boolean depMarginal = false;
        boolean destMarginal = false;

        for (MetarData metar : metarList) {
            String status = checkVfrMinimums(metar);
            boolean isDep = metar.getAirportId().equals(dep);

            if (status.contains("WARNING")) {
                if (isDep) depOk = false;
                else destOk = false;
            }
            else if (status.contains("CAUTION")) {
                if (isDep) depMarginal = true;
                else destMarginal = true;
            }
        }

        if (!depOk && !destOk) {
            return "NO-GO — Both " + dep + " and " + dest + " are below VFR minimums.";
        }
        else if (!depOk) {
            return "NO-GO — Departure " + dep + " is below VFR minimums.";
        }
        else if (!destOk) {
            return "NO-GO — Destination " + dest + " is below VFR minimums.";
        }
        else if (depMarginal && destMarginal) {
            return "CAUTION — Both airports reporting marginal VFR. Proceed carefully.";
        }
        else if (depMarginal) {
            return "CAUTION — Departure " + dep + " is marginal VFR.";
        }
        else if (destMarginal) {
            return "CAUTION — Destination " + dest + " is marginal VFR.";
        }
        else {
            return "GO — Both " + dep + " and " + dest + " are VFR. Good to fly!";
        }
    }

    //===== RUNWAY SECTION BUILDER =====
    private VBox buildRunwaySection(MetarData metar, List<Runway> runways, String colorHex) {
        VBox section = new VBox(6);
        section.setPadding(new Insets(8, 0, 0, 0));
        section.getChildren().add(new Separator());

        int windDir = metar.getWindDir();
        int windSpeed = metar.getWindSpeed();

        //===== NO RUNWAY DATA — MANUAL FALLBACK =====
        if (runways.isEmpty()) {
            Label noData = makeInfoLabel("Runway data not available — enter heading manually:");
            noData.setStyle("-fx-text-fill: #888888; -fx-font-size: 13px;");

            TextField manualInput = new TextField();
            manualInput.setPromptText("ex: 17");
            manualInput.setMaxWidth(80);
            manualInput.setStyle(
                    "-fx-background-color: #2a2a2a; -fx-text-fill: white; " +
                            "-fx-prompt-text-fill: #555555; -fx-font-size: 13px;"
            );

            Label resultLabel = makeInfoLabel("");

            Button calcBtn = new Button("Calculate");
            calcBtn.setStyle(
                    "-fx-background-color: #4ea8de; -fx-text-fill: white; " +
                            "-fx-font-size: 12px; -fx-padding: 5px 12px;"
            );
            calcBtn.setOnAction(e -> {
                try {
                    int rwy = Integer.parseInt(manualInput.getText().trim());
                    if (rwy < 1 || rwy > 36) {
                        resultLabel.setText("Invalid — enter 1 to 36.");
                        return;
                    }
                    double[] components = CrosswindCalculator.calculate(
                            rwy * 10, windDir, windSpeed);
                    resultLabel.setText(formatCrosswindResult(components));
                }
                catch (NumberFormatException ex) {
                    resultLabel.setText("Invalid input.");
                }
            });

            section.getChildren().addAll(noData, new HBox(8, manualInput, calcBtn), resultLabel);
            return section;
        }

        //===== RUNWAY ANALYSIS =====
        //sort by lowest crosswind component — best runway first
        runways.sort((a, b) -> {
            double crossA = Math.abs(CrosswindCalculator.calculate(a.getHeading(), windDir, windSpeed)[1]);
            double crossB = Math.abs(CrosswindCalculator.calculate(b.getHeading(), windDir, windSpeed)[1]);
            return Double.compare(crossA, crossB);
        });

        Label runwayHeader = makeInfoLabel("Runway Analysis:");
        runwayHeader.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        runwayHeader.setTextFill(Color.web("#aaaaaa"));
        section.getChildren().add(runwayHeader);

        for (int i = 0; i < runways.size(); i++) {
            Runway rwy = runways.get(i);
            double[] components = CrosswindCalculator.calculate(rwy.getHeading(), windDir, windSpeed);
            double headwind = components[0];
            double crosswind = Math.abs(components[1]);
            String side = components[1] >= 0 ? "R" : "L";
            boolean isBest = (i == 0);

            String hwLabel = headwind >= 0
                    ? String.format("HW: %.1f kts", headwind)
                    : String.format("TW: %.1f kts", Math.abs(headwind));

            String runwayText = String.format("%sRwy %s   %s   XW: %.1f kts %s",
                    isBest ? ">> Best  " : "        ",
                    rwy.getIdent(), hwLabel, crosswind, side);

            Label rwyLabel = new Label(runwayText);
            rwyLabel.setStyle(isBest
                    ? "-fx-text-fill: " + colorHex + "; -fx-font-family: 'Courier New'; -fx-font-size: 13px; -fx-font-weight: bold;"
                    : "-fx-text-fill: #cccccc; -fx-font-family: 'Courier New'; -fx-font-size: 13px;");
            section.getChildren().add(rwyLabel);
        }

        return section;
    }

    //===== VFR MINIMUMS CHECKER =====
    //VFR requires: visibility >= 3SM and ceiling >= 1000ft
    //marginal VFR: visibility 3-5SM or ceiling 1000-3000ft
    private String checkVfrMinimums(MetarData metar) {
        float visibility = metar.getVisibSM();
        int lowestCeiling = Integer.MAX_VALUE;
        String clouds = metar.getCloudLayers();

        //only BKN and OVC count as a ceiling
        if (clouds.contains("BKN") || clouds.contains("OVC")) {
            for (String layer : clouds.split(", ")) {
                if (layer.contains("BKN") || layer.contains("OVC")) {
                    try {
                        String numStr = layer.replaceAll("[^0-9]", "");
                        if (!numStr.isEmpty()) {
                            int alt = Integer.parseInt(numStr);
                            if (alt < lowestCeiling) lowestCeiling = alt;
                        }
                    }
                    catch (NumberFormatException ignored) {}
                }
            }
        }

        if (visibility < 3.0f || lowestCeiling < 1000) {
            return "⚠ WARNING: Below VFR minimums — IFR conditions";
        }
        else if (visibility < 5.0f || lowestCeiling < 3000) {
            return "⚠ CAUTION: Marginal VFR conditions";
        }
        else {
            return "✓ VFR conditions meet minimums";
        }
    }

    //===== HELPERS =====
    //creates a standard styled info label
    private Label makeInfoLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.web("#cccccc"));
        label.setFont(Font.font("Arial", 13));
        label.setWrapText(true);
        return label;
    }

    //formats crosswind result for manual runway input display
    private String formatCrosswindResult(double[] components) {
        double headwind = components[0];
        double crosswind = components[1];
        String hwLabel = headwind >= 0 ? "Headwind" : "Tailwind";
        String side = crosswind >= 0 ? "from right" : "from left";
        return String.format("%s: %.1f kts  |  Crosswind: %.1f kts %s",
                hwLabel, Math.abs(headwind), Math.abs(crosswind), side);
    }

    //===== ENTRY POINT =====
    public static void main(String[] args) {
        launch(args);
    }
}