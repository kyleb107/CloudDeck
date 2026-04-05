package com.kylebarnes.clouddeck.storage;

import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.DistanceUnit;
import com.kylebarnes.clouddeck.model.TemperatureUnit;
import com.kylebarnes.clouddeck.model.ThemePreset;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class LocalSettingsRepository implements SettingsRepository {
    private static final Path SETTINGS_FILE = Path.of(
            System.getProperty("user.home"),
            ".clouddeck",
            "settings.properties"
    );

    @Override
    public synchronized AppSettings loadSettings() {
        AppSettings defaults = AppSettings.defaults();
        if (!Files.exists(SETTINGS_FILE)) {
            return defaults;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(SETTINGS_FILE, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return new AppSettings(
                    properties.getProperty("homeAirport", defaults.homeAirport()).trim().toUpperCase(),
                    properties.getProperty("defaultAircraftName", defaults.defaultAircraftName()).trim(),
                    parseThemePreset(properties.getProperty("themePreset"), defaults.themePreset()),
                    parseTemperatureUnit(properties.getProperty("temperatureUnit"), defaults.temperatureUnit()),
                    parseDistanceUnit(properties.getProperty("distanceUnit"), defaults.distanceUnit()),
                    parseDouble(properties.getProperty("taxiFuelGallons"), defaults.taxiFuelGallons()),
                    parseDouble(properties.getProperty("climbFuelGallons"), defaults.climbFuelGallons()),
                    parseInt(properties.getProperty("groundspeedAdjustmentKts"), defaults.groundspeedAdjustmentKts()),
                    parseFloat(properties.getProperty("vfrWarningVisibilitySm"), defaults.vfrWarningVisibilitySm()),
                    parseInt(properties.getProperty("vfrWarningCeilingFt"), defaults.vfrWarningCeilingFt()),
                    parseFloat(properties.getProperty("vfrCautionVisibilitySm"), defaults.vfrCautionVisibilitySm()),
                    parseInt(properties.getProperty("vfrCautionCeilingFt"), defaults.vfrCautionCeilingFt()),
                    parseInt(properties.getProperty("densityAltitudeCautionFt"), defaults.densityAltitudeCautionFt()),
                    parseInt(properties.getProperty("densityAltitudeWarningFt"), defaults.densityAltitudeWarningFt())
            );
        } catch (IOException exception) {
            System.out.println("Could not load settings: " + exception.getMessage());
            return defaults;
        }
    }

    @Override
    public synchronized void saveSettings(AppSettings settings) {
        Properties properties = new Properties();
        properties.setProperty("homeAirport", settings.homeAirport());
        properties.setProperty("defaultAircraftName", settings.defaultAircraftName());
        properties.setProperty("themePreset", settings.themePreset().name());
        properties.setProperty("temperatureUnit", settings.temperatureUnit().name());
        properties.setProperty("distanceUnit", settings.distanceUnit().name());
        properties.setProperty("taxiFuelGallons", String.valueOf(settings.taxiFuelGallons()));
        properties.setProperty("climbFuelGallons", String.valueOf(settings.climbFuelGallons()));
        properties.setProperty("groundspeedAdjustmentKts", String.valueOf(settings.groundspeedAdjustmentKts()));
        properties.setProperty("vfrWarningVisibilitySm", String.valueOf(settings.vfrWarningVisibilitySm()));
        properties.setProperty("vfrWarningCeilingFt", String.valueOf(settings.vfrWarningCeilingFt()));
        properties.setProperty("vfrCautionVisibilitySm", String.valueOf(settings.vfrCautionVisibilitySm()));
        properties.setProperty("vfrCautionCeilingFt", String.valueOf(settings.vfrCautionCeilingFt()));
        properties.setProperty("densityAltitudeCautionFt", String.valueOf(settings.densityAltitudeCautionFt()));
        properties.setProperty("densityAltitudeWarningFt", String.valueOf(settings.densityAltitudeWarningFt()));

        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(SETTINGS_FILE, StandardCharsets.UTF_8)) {
                properties.store(writer, "CloudDeck settings");
            }
        } catch (IOException exception) {
            System.out.println("Could not save settings: " + exception.getMessage());
        }
    }

    private TemperatureUnit parseTemperatureUnit(String rawValue, TemperatureUnit fallback) {
        try {
            return rawValue == null ? fallback : TemperatureUnit.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private ThemePreset parseThemePreset(String rawValue, ThemePreset fallback) {
        try {
            return rawValue == null ? fallback : ThemePreset.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private DistanceUnit parseDistanceUnit(String rawValue, DistanceUnit fallback) {
        try {
            return rawValue == null ? fallback : DistanceUnit.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private int parseInt(String rawValue, int fallback) {
        try {
            return rawValue == null ? fallback : Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private float parseFloat(String rawValue, float fallback) {
        try {
            return rawValue == null ? fallback : Float.parseFloat(rawValue.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private double parseDouble(String rawValue, double fallback) {
        try {
            return rawValue == null ? fallback : Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
