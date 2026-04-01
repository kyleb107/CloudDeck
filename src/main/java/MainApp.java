/* Designed by: Kyle Barnes
   JavaFX entry point for CloudDeck — card-based GUI with color coded flight
   categories and auto-sorted runway crosswind analysis
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainApp extends Application {

    //fetcher and parser instances
    private MetarFetcher fetcher = new MetarFetcher();
    private MetarParser parser = new MetarParser();
    private RunwayFetcher runwayFetcher = new RunwayFetcher();

    @Override
    public void start(Stage stage) {

        //===== HEADER =====
        Label title = new Label("CloudDeck");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        title.setTextFill(Color.web("#4ea8de"));

        Label subtitle = new Label("Aviation Weather Tool");
        subtitle.setFont(Font.font("Arial", 14));
        subtitle.setTextFill(Color.web("#888888"));

        //===== INPUT ROW =====
        TextField airportInput = new TextField();
        airportInput.setPromptText("Enter ICAO ID(s) ex: KDFW,KAUS,KLFK");
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

        //===== RESULTS CONTAINER =====
        //scrollable container that holds airport cards
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

            //clear previous results
            cardsContainer.getChildren().clear();
            statusLabel.setText("Fetching weather and runway data...");

            //run on background thread so UI doesn't freeze
            new Thread(() -> {
                try {
                    org.json.JSONArray results = fetcher.fetchRaw(input);
                    List<MetarData> metarList = new ArrayList<>();

                    for (int i = 0; i < results.length(); i++) {
                        metarList.add(parser.parse(results.getJSONObject(i)));
                    }

                    //sort to match user input order
                    List<String> inputOrder = Arrays.asList(input.toUpperCase().split(","));
                    metarList.sort((a, b) ->
                            inputOrder.indexOf(a.getAirportId()) - inputOrder.indexOf(b.getAirportId())
                    );

                    //build a card for each airport
                    List<VBox> cards = new ArrayList<>();
                    for (MetarData metar : metarList) {
                        List<Runway> runways = runwayFetcher.fetchRunways(metar.getAirportId());
                        VBox card = buildAirportCard(metar, runways);
                        cards.add(card);
                    }

                    //update UI on JavaFX thread
                    Platform.runLater(() -> {
                        cardsContainer.getChildren().addAll(cards);
                        statusLabel.setText("");
                    });
                }
                catch (Exception ex) {
                    Platform.runLater(() ->
                            statusLabel.setText("Error: " + ex.getMessage())
                    );
                }
            }).start();
        });

        //===== LAYOUT =====
        HBox inputRow = new HBox(10, airportInput, fetchButton);
        inputRow.setAlignment(Pos.CENTER);

        VBox root = new VBox(10, title, subtitle, inputRow, statusLabel, scrollPane);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: #121212;");

        //===== SCENE =====
        Scene scene = new Scene(root, 750, 620);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setTitle("CloudDeck");
        stage.setScene(scene);
        stage.show();
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

        //===== CATEGORY COLOR =====
        String category = metar.getFlightCategory();
        Color categoryColor = switch (category) {
            case "VFR"  -> Color.web("#00cc44");   //green
            case "MVFR" -> Color.web("#4ea8de");   //blue
            case "IFR"  -> Color.web("#ff4444");   //red
            case "LIFR" -> Color.web("#cc44cc");   //purple
            default     -> Color.web("#888888");   //grey
        };

        //===== HEADER ROW =====
        //colored dot indicating flight category
        Label dot = new Label("●");
        dot.setTextFill(categoryColor);
        dot.setFont(Font.font("Arial", FontWeight.BOLD, 20));

        //airport ID and category
        Label airportLabel = new Label(metar.getAirportId() + " — " + category);
        airportLabel.setTextFill(Color.WHITE);
        airportLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        HBox headerRow = new HBox(8, dot, airportLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        //===== WEATHER INFO =====
        //wind string — include gusts if present
        String windStr = metar.getWindGust() > 0
                ? String.format("Wind: %03d° at %d kts gusting %d kts",
                metar.getWindDir(), metar.getWindSpeed(), metar.getWindGust())
                : String.format("Wind: %03d° at %d kts",
                metar.getWindDir(), metar.getWindSpeed());

        Label windLabel = makeInfoLabel(windStr);
        Label altLabel = makeInfoLabel(String.format(
                "Altimeter: %.2f inHg  |  Clouds: %s", metar.getAltimeter(), metar.getCloudLayers()
        ));
        Label timeLabel = makeInfoLabel("Observation: " + metar.getObservationTime().replace("T", " ").replace(".000Z", "Z"));
        Label rawLabel = makeInfoLabel("Raw: " + metar.getRawOb());
        rawLabel.setTextFill(Color.web("#666666"));
        rawLabel.setFont(Font.font("Courier New", 11));
        rawLabel.setWrapText(true);

        //===== RUNWAY SECTION =====
        VBox runwaySection = buildRunwaySection(metar, runways);

        //assemble card
        card.getChildren().addAll(headerRow, windLabel, altLabel, timeLabel, rawLabel, runwaySection);
        return card;
    }

    //===== RUNWAY SECTION BUILDER =====
    private VBox buildRunwaySection(MetarData metar, List<Runway> runways) {
        VBox section = new VBox(6);
        section.setPadding(new Insets(8, 0, 0, 0));

        //divider line
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #333333;");
        section.getChildren().add(sep);

        if (runways.isEmpty()) {
            Label noData = makeInfoLabel("Runway data not available — enter heading manually:");
            noData.setTextFill(Color.web("#888888"));

            //manual runway input fallback
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
                            rwy * 10, metar.getWindDir(), metar.getWindSpeed()
                    );
                    resultLabel.setText(formatCrosswindResult(components));
                }
                catch (NumberFormatException ex) {
                    resultLabel.setText("Invalid input.");
                }
            });

            HBox manualRow = new HBox(8, manualInput, calcBtn);
            section.getChildren().addAll(noData, manualRow, resultLabel);
            return section;
        }

        //sort runways by crosswind component (lowest first = best runway)
        int windDir = metar.getWindDir();
        int windSpeed = metar.getWindSpeed();

        runways.sort((a, b) -> {
            double crossA = Math.abs(CrosswindCalculator.calculate(a.getHeading(), windDir, windSpeed)[1]);
            double crossB = Math.abs(CrosswindCalculator.calculate(b.getHeading(), windDir, windSpeed)[1]);
            return Double.compare(crossA, crossB);
        });

        Label runwayHeader = makeInfoLabel("Runway Analysis:");
        runwayHeader.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        runwayHeader.setTextFill(Color.web("#aaaaaa"));
        section.getChildren().add(runwayHeader);

        //display each runway sorted best to worst
        for (int i = 0; i < runways.size(); i++) {
            Runway rwy = runways.get(i);
            double[] components = CrosswindCalculator.calculate(rwy.getHeading(), windDir, windSpeed);
            double headwind = components[0];
            double crosswind = Math.abs(components[1]);
            String side = components[1] >= 0 ? "R" : "L";

            boolean isBest = (i == 0);
            String prefix = isBest ? "✅ Best  " : "        ";
            String hwLabel = headwind >= 0
                    ? String.format("HW: %.1f kts", headwind)
                    : String.format("TW: %.1f kts", Math.abs(headwind));

            String runwayText = String.format(
                    "%sRwy %s   %s   XW: %.1f kts %s",
                    prefix, rwy.getIdent(), hwLabel, crosswind, side
            );

            Label rwyLabel = new Label(runwayText);
            rwyLabel.setFont(Font.font("Courier New", 13));
            rwyLabel.setTextFill(isBest ? Color.web("#00cc44") : Color.web("#cccccc"));
            section.getChildren().add(rwyLabel);
        }

        return section;
    }

    //===== HELPERS =====
    private Label makeInfoLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.web("#cccccc"));
        label.setFont(Font.font("Arial", 13));
        label.setWrapText(true);
        return label;
    }

    private String formatCrosswindResult(double[] components) {
        double headwind = components[0];
        double crosswind = components[1];
        String hwLabel = headwind >= 0 ? "Headwind" : "Tailwind";
        String side = crosswind >= 0 ? "from right" : "from left";
        return String.format("%s: %.1f kts  |  Crosswind: %.1f kts %s",
                hwLabel, Math.abs(headwind), Math.abs(crosswind), side);
    }

    public static void main(String[] args) {
        launch(args);
    }
}