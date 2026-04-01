/* Designed by: Kyle Barnes
   JavaFX entry point for CloudDeck — builds and displays the main GUI window
 */

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class MainApp extends Application {

    //fetcher and parser instances used to retrieve and process METAR data
    private MetarFetcher fetcher = new MetarFetcher();
    private MetarParser parser = new MetarParser();

    @Override
    public void start(Stage stage) {

        //===== HEADER =====
        //main title label styled in blue
        Label title = new Label("CloudDeck");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #4ea8de;");

        //subtitle below the main title
        Label subtitle = new Label("Aviation Weather Tool");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #888888;");

        //===== INPUT ROW =====
        //text field where user types ICAO airport ID(s) ex: KDFW,KAUS
        TextField airportInput = new TextField();
        airportInput.setPromptText("Enter ICAO ID(s) ex: KDFW,KAUS,KLFK");
        airportInput.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: white; -fx-prompt-text-fill: #666666; -fx-font-size: 14px; -fx-padding: 10px;");
        airportInput.setMaxWidth(400);

        //button that triggers the METAR fetch when clicked
        Button fetchButton = new Button("Get Weather");
        fetchButton.setStyle("-fx-background-color: #4ea8de; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10px 20px; -fx-cursor: hand;");

        //===== RESULTS AREA =====
        //read-only text area that displays parsed METAR results
        TextArea resultsArea = new TextArea();
        resultsArea.setEditable(false);
        resultsArea.getStyleClass().add("text-area");
        resultsArea.setWrapText(true);
        resultsArea.setPrefHeight(400);

        //===== BUTTON ACTION =====
        fetchButton.setOnAction(e -> {
            String input = airportInput.getText().trim();

            //validate that the user entered something
            if (input.isEmpty()) {
                resultsArea.setText("Please enter at least one ICAO airport ID.");
                return;
            }

            resultsArea.setText("Fetching weather data...");

            //run the API call on a background thread so the UI doesn't freeze
            new Thread(() -> {
                try {
                    //fetch raw JSON array from FAA API
                    org.json.JSONArray results = fetcher.fetchRaw(input);
                    java.util.List<MetarData> metarList = new java.util.ArrayList<>();

                    //parse each result into a MetarData object
                    for (int i = 0; i < results.length(); i++) {
                        metarList.add(parser.parse(results.getJSONObject(i)));
                    }

                    //sort results to match the order the user typed them
                    java.util.List<String> inputOrder = java.util.Arrays.asList(input.toUpperCase().split(","));
                    metarList.sort((a, b) -> inputOrder.indexOf(a.getAirportId()) - inputOrder.indexOf(b.getAirportId()));

                    //build the output string for the results area
                    StringBuilder sb = new StringBuilder();
                    for (MetarData metar : metarList) {
                        sb.append("=".repeat(60)).append("\n");
                        sb.append(metar.toString()).append("\n");
                        sb.append("Raw METAR: ").append(metar.getRawOb()).append("\n");
                    }

                    //update the UI on the JavaFX application thread
                    javafx.application.Platform.runLater(() -> resultsArea.setText(sb.toString()));
                }
                catch (Exception ex) {
                    //display error message in results area if fetch fails
                    javafx.application.Platform.runLater(() -> resultsArea.setText("Error: " + ex.getMessage()));
                }
            }).start();
        });

        //===== LAYOUT =====
        //horizontal row containing the input field and fetch button
        HBox inputRow = new HBox(10, airportInput, fetchButton);
        inputRow.setAlignment(Pos.CENTER);

        //vertical layout stacking all elements top to bottom
        VBox root = new VBox(10, title, subtitle, inputRow, resultsArea);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: #121212;");

        //===== SCENE AND STAGE =====
        //scene defines the window size, stage is the actual window
        Scene scene = new Scene(root, 700, 550);
        stage.setTitle("CloudDeck");
        stage.setScene(scene);
        //apply external css for text area dark mode styling
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.show();
    }

    public static void main(String[] args) {
        //launch the JavaFX application
        launch(args);
    }
}