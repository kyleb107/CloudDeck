package com.kylebarnes.clouddeck.ui;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AltimeterUnit;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.DistanceUnit;
import com.kylebarnes.clouddeck.model.TemperatureUnit;
import com.kylebarnes.clouddeck.model.ThemePreset;
import com.kylebarnes.clouddeck.model.TimeDisplayMode;
import com.kylebarnes.clouddeck.model.WindUnit;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class SettingsView {

    private final AppContext ctx;
    private final UiHelper ui;
    private final Consumer<String> openUrl;
    private final Runnable onSettingsSaved;

    public SettingsView(AppContext ctx, UiHelper ui, Consumer<String> openUrl, Runnable onSettingsSaved) {
        this.ctx = ctx;
        this.ui = ui;
        this.openUrl = openUrl;
        this.onSettingsSaved = onSettingsSaved;
    }

    public ScrollPane build(Stage stage, String savedWeatherInput, String savedRouteDeparture, String savedRouteDestination, String savedRouteTime) {
        Label sectionTitle = ui.createSectionTitle("Settings");
        Label sectionSubtitle = ui.createSectionSubtitle("Define defaults and thresholds that future planning features can reuse.");
        AppSettings defaultSettings = AppSettings.defaults();

        TextField homeAirportField = ui.createInputField("Home airport ICAO", 220);
        homeAirportField.setText(ctx.appSettings.homeAirport());
        ui.attachAutocomplete(homeAirportField, stage);

        ComboBox<ThemePreset> themePresetBox = new ComboBox<>();
        themePresetBox.getItems().setAll(ThemePreset.values());
        themePresetBox.setValue(ctx.appSettings.themePreset());
        themePresetBox.setPrefWidth(220);

        ComboBox<TemperatureUnit> temperatureUnitBox = new ComboBox<>();
        temperatureUnitBox.getItems().setAll(TemperatureUnit.values());
        temperatureUnitBox.setValue(ctx.appSettings.temperatureUnit());
        temperatureUnitBox.setPrefWidth(220);

        ComboBox<DistanceUnit> distanceUnitBox = new ComboBox<>();
        distanceUnitBox.getItems().setAll(DistanceUnit.values());
        distanceUnitBox.setValue(ctx.appSettings.distanceUnit());
        distanceUnitBox.setPrefWidth(220);

        ComboBox<WindUnit> windUnitBox = new ComboBox<>();
        windUnitBox.getItems().setAll(WindUnit.values());
        windUnitBox.setValue(ctx.appSettings.windUnit());
        windUnitBox.setPrefWidth(220);

        ComboBox<AltimeterUnit> altimeterUnitBox = new ComboBox<>();
        altimeterUnitBox.getItems().setAll(AltimeterUnit.values());
        altimeterUnitBox.setValue(ctx.appSettings.altimeterUnit());
        altimeterUnitBox.setPrefWidth(220);

        ComboBox<TimeDisplayMode> timeDisplayModeBox = new ComboBox<>();
        timeDisplayModeBox.getItems().setAll(TimeDisplayMode.values());
        timeDisplayModeBox.setValue(ctx.appSettings.timeDisplayMode());
        timeDisplayModeBox.setPrefWidth(220);

        ComboBox<AircraftProfile> defaultAircraftBox = new ComboBox<>();
        defaultAircraftBox.getItems().setAll(ctx.aircraftProfileRepository.loadProfiles());
        defaultAircraftBox.setPrefWidth(260);
        for (AircraftProfile profile : defaultAircraftBox.getItems()) {
            if (profile.name().equalsIgnoreCase(ctx.appSettings.defaultAircraftName())) {
                defaultAircraftBox.setValue(profile);
                break;
            }
        }
        if (defaultAircraftBox.getValue() == null && !defaultAircraftBox.getItems().isEmpty()) {
            defaultAircraftBox.setValue(defaultAircraftBox.getItems().getFirst());
        }

        TextField warningVisibilityField = ui.createInputField("Warning visibility SM", 160);
        warningVisibilityField.setText(String.valueOf(ctx.appSettings.vfrWarningVisibilitySm()));
        TextField warningCeilingField = ui.createInputField("Warning ceiling ft", 160);
        warningCeilingField.setText(String.valueOf(ctx.appSettings.vfrWarningCeilingFt()));
        TextField cautionVisibilityField = ui.createInputField("Caution visibility SM", 160);
        cautionVisibilityField.setText(String.valueOf(ctx.appSettings.vfrCautionVisibilitySm()));
        TextField cautionCeilingField = ui.createInputField("Caution ceiling ft", 160);
        cautionCeilingField.setText(String.valueOf(ctx.appSettings.vfrCautionCeilingFt()));
        TextField densityCautionField = ui.createInputField("Density caution ft", 160);
        densityCautionField.setText(String.valueOf(ctx.appSettings.densityAltitudeCautionFt()));
        TextField densityWarningField = ui.createInputField("Density warning ft", 160);
        densityWarningField.setText(String.valueOf(ctx.appSettings.densityAltitudeWarningFt()));
        TextField taxiFuelField = ui.createInputField("Taxi fuel gal", 160);
        taxiFuelField.setText(ui.formatOneDecimal(ctx.appSettings.taxiFuelGallons()));
        TextField climbFuelField = ui.createInputField("Climb fuel gal", 160);
        climbFuelField.setText(ui.formatOneDecimal(ctx.appSettings.climbFuelGallons()));
        TextField groundspeedAdjustmentField = ui.createInputField("Groundspeed adjustment kt", 160);
        groundspeedAdjustmentField.setText(String.valueOf(ctx.appSettings.groundspeedAdjustmentKts()));

        Label settingsStatus = ui.createMutedLabel(ctx.settingsStatusMessage);
        Button resetDefaultsButton = ui.createGhostButton("Reset to Defaults");
        Button saveSettingsButton = ui.createPrimaryButton("Save Settings");

        resetDefaultsButton.setOnAction(event -> {
            homeAirportField.setText(defaultSettings.homeAirport());
            themePresetBox.setValue(defaultSettings.themePreset());
            temperatureUnitBox.setValue(defaultSettings.temperatureUnit());
            distanceUnitBox.setValue(defaultSettings.distanceUnit());
            windUnitBox.setValue(defaultSettings.windUnit());
            altimeterUnitBox.setValue(defaultSettings.altimeterUnit());
            timeDisplayModeBox.setValue(defaultSettings.timeDisplayMode());

            AircraftProfile defaultProfile = defaultAircraftBox.getItems().stream()
                    .filter(profile -> profile.name().equalsIgnoreCase(defaultSettings.defaultAircraftName()))
                    .findFirst()
                    .orElse(defaultAircraftBox.getItems().isEmpty() ? null : defaultAircraftBox.getItems().getFirst());
            defaultAircraftBox.setValue(defaultProfile);

            warningVisibilityField.setText(String.valueOf(defaultSettings.vfrWarningVisibilitySm()));
            warningCeilingField.setText(String.valueOf(defaultSettings.vfrWarningCeilingFt()));
            cautionVisibilityField.setText(String.valueOf(defaultSettings.vfrCautionVisibilitySm()));
            cautionCeilingField.setText(String.valueOf(defaultSettings.vfrCautionCeilingFt()));
            densityCautionField.setText(String.valueOf(defaultSettings.densityAltitudeCautionFt()));
            densityWarningField.setText(String.valueOf(defaultSettings.densityAltitudeWarningFt()));
            taxiFuelField.setText(ui.formatOneDecimal(defaultSettings.taxiFuelGallons()));
            climbFuelField.setText(ui.formatOneDecimal(defaultSettings.climbFuelGallons()));
            groundspeedAdjustmentField.setText(String.valueOf(defaultSettings.groundspeedAdjustmentKts()));

            ctx.settingsStatusMessage = "";
            settingsStatus.setText("Defaults restored in the form. Click Save Settings to apply them.");
        });

        saveSettingsButton.setOnAction(event -> {
            try {
                AircraftProfile defaultProfile = defaultAircraftBox.getValue();
                AppSettings updatedSettings = new AppSettings(
                        homeAirportField.getText().trim().toUpperCase(),
                        defaultProfile == null ? "" : defaultProfile.name(),
                        themePresetBox.getValue(),
                        temperatureUnitBox.getValue(),
                        distanceUnitBox.getValue(),
                        windUnitBox.getValue(),
                        altimeterUnitBox.getValue(),
                        timeDisplayModeBox.getValue(),
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

                ctx.appSettings = updatedSettings;
                ctx.themePalette = ThemePalette.forPreset(ctx.appSettings.themePreset());
                ctx.settingsStatusMessage = "Settings saved.";
                onSettingsSaved.run();
            } catch (NumberFormatException exception) {
                ctx.settingsStatusMessage = "";
                settingsStatus.setText("Use valid numbers for thresholds and route assumptions.");
            }
        });

        GridPane defaultsGrid = new GridPane();
        defaultsGrid.setHgap(14);
        defaultsGrid.setVgap(12);
        defaultsGrid.add(ui.formLabel("Home Airport"), 0, 0);
        defaultsGrid.add(homeAirportField, 1, 0);
        defaultsGrid.add(ui.formLabel("Default Aircraft"), 0, 1);
        defaultsGrid.add(defaultAircraftBox, 1, 1);
        defaultsGrid.add(ui.formLabel("Theme"), 0, 2);
        defaultsGrid.add(themePresetBox, 1, 2);
        defaultsGrid.add(ui.formLabel("Temperature Unit"), 0, 3);
        defaultsGrid.add(temperatureUnitBox, 1, 3);
        defaultsGrid.add(ui.formLabel("Distance Unit"), 0, 4);
        defaultsGrid.add(distanceUnitBox, 1, 4);
        defaultsGrid.add(ui.formLabel("Wind Unit"), 0, 5);
        defaultsGrid.add(windUnitBox, 1, 5);
        defaultsGrid.add(ui.formLabel("Altimeter Unit"), 0, 6);
        defaultsGrid.add(altimeterUnitBox, 1, 6);
        defaultsGrid.add(ui.formLabel("Time Display"), 0, 7);
        defaultsGrid.add(timeDisplayModeBox, 1, 7);

        GridPane thresholdsGrid = new GridPane();
        thresholdsGrid.setHgap(14);
        thresholdsGrid.setVgap(12);
        thresholdsGrid.add(ui.formLabel("VFR Warning Vis"), 0, 0);
        thresholdsGrid.add(warningVisibilityField, 1, 0);
        thresholdsGrid.add(ui.formLabel("VFR Warning Ceiling"), 2, 0);
        thresholdsGrid.add(warningCeilingField, 3, 0);
        thresholdsGrid.add(ui.formLabel("VFR Caution Vis"), 0, 1);
        thresholdsGrid.add(cautionVisibilityField, 1, 1);
        thresholdsGrid.add(ui.formLabel("VFR Caution Ceiling"), 2, 1);
        thresholdsGrid.add(cautionCeilingField, 3, 1);
        thresholdsGrid.add(ui.formLabel("DA Caution"), 0, 2);
        thresholdsGrid.add(densityCautionField, 1, 2);
        thresholdsGrid.add(ui.formLabel("DA Warning"), 2, 2);
        thresholdsGrid.add(densityWarningField, 3, 2);

        GridPane planningGrid = new GridPane();
        planningGrid.setHgap(14);
        planningGrid.setVgap(12);
        planningGrid.add(ui.formLabel("Taxi Fuel"), 0, 0);
        planningGrid.add(taxiFuelField, 1, 0);
        planningGrid.add(ui.formLabel("Climb Fuel"), 2, 0);
        planningGrid.add(climbFuelField, 3, 0);
        planningGrid.add(ui.formLabel("Groundspeed Adj"), 0, 1);
        planningGrid.add(groundspeedAdjustmentField, 1, 1);

        VBox defaultsCard = ui.createPanel(
                "Defaults",
                "Set values the app should restore at launch.",
                defaultsGrid
        );
        VBox thresholdsCard = ui.createPanel(
                "Thresholds",
                "These feed current VFR and density altitude advisories.",
                thresholdsGrid
        );
        VBox planningCard = ui.createPanel(
                "Route Assumptions",
                "Applied to direct-route fuel and ETA calculations for every aircraft profile.",
                planningGrid
        );

        HBox settingsActions = new HBox(10, resetDefaultsButton, saveSettingsButton);
        settingsActions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(16, sectionTitle, sectionSubtitle, defaultsCard, thresholdsCard, planningCard, settingsActions, settingsStatus);
        content.setPadding(new Insets(24));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }
}
