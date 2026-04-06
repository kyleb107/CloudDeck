package com.kylebarnes.clouddeck.ui;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class AircraftView {

    private final AppContext ctx;
    private final UiHelper ui;

    public AircraftView(AppContext ctx, UiHelper ui) {
        this.ctx = ctx;
        this.ui = ui;
    }

    public ScrollPane build() {
        Label sectionTitle = ui.createSectionTitle("Aircraft Hangar");
        Label sectionSubtitle = ui.createSectionSubtitle("Create reusable aircraft profiles for fuel planning and crosswind alerts.");

        Button removeSelectedButton = ui.createSecondaryButton("Remove Selected");
        Label aircraftStatus = ui.createMutedLabel("");

        removeSelectedButton.setOnAction(event -> {
            AircraftProfile selectedProfile = ctx.aircraftSelector.getValue();
            if (selectedProfile == null) {
                aircraftStatus.setText("Select a profile to remove.");
                return;
            }

            ctx.aircraftProfileRepository.removeProfile(selectedProfile.name());
            reloadAircraftProfiles(null);
            aircraftStatus.setText("Removed profile: " + selectedProfile.name());
            updateAircraftDisplays();
        });

        TextField nameField = ui.createInputField("Aircraft name", 240);
        TextField cruiseField = ui.createInputField("Cruise speed (kts)", 160);
        TextField burnField = ui.createInputField("Fuel burn (gph)", 160);
        TextField usableFuelField = ui.createInputField("Usable fuel (gal)", 160);
        TextField reserveField = ui.createInputField("Reserve target (gal)", 160);
        TextField crosswindField = ui.createInputField("Max crosswind (kts)", 160);
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Notes, equipment, or planning caveats");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);

        Button saveButton = ui.createPrimaryButton("Save Profile");
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

                ctx.aircraftProfileRepository.saveProfile(profile);
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
        formGrid.add(ui.formLabel("Aircraft"), 0, 0);
        formGrid.add(nameField, 1, 0);
        formGrid.add(ui.formLabel("Cruise"), 0, 1);
        formGrid.add(cruiseField, 1, 1);
        formGrid.add(ui.formLabel("Burn"), 2, 1);
        formGrid.add(burnField, 3, 1);
        formGrid.add(ui.formLabel("Usable Fuel"), 0, 2);
        formGrid.add(usableFuelField, 1, 2);
        formGrid.add(ui.formLabel("Reserve"), 2, 2);
        formGrid.add(reserveField, 3, 2);
        formGrid.add(ui.formLabel("Max Crosswind"), 0, 3);
        formGrid.add(crosswindField, 1, 3);
        formGrid.add(ui.formLabel("Notes"), 0, 4);
        formGrid.add(notesArea, 1, 4, 3, 1);

        VBox selectionCard = ui.createPanel(
                "Active Profile",
                "Switch profiles globally from the header or manage the active one here.",
                new HBox(10, ui.aircraftSelectorPlaceholder(), removeSelectedButton),
                ctx.aircraftSummaryBox
        );

        VBox formCard = ui.createPanel(
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

    private void updateAircraftDisplays() {
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

    private void reloadAircraftProfiles(String selectedName) {
        java.util.List<AircraftProfile> profiles = ctx.aircraftProfileRepository.loadProfiles();
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
}
