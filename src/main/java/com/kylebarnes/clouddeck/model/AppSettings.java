package com.kylebarnes.clouddeck.model;

public record AppSettings(
        String homeAirport,
        String defaultAircraftName,
        ThemePreset themePreset,
        TemperatureUnit temperatureUnit,
        DistanceUnit distanceUnit,
        double taxiFuelGallons,
        double climbFuelGallons,
        int groundspeedAdjustmentKts,
        float vfrWarningVisibilitySm,
        int vfrWarningCeilingFt,
        float vfrCautionVisibilitySm,
        int vfrCautionCeilingFt,
        int densityAltitudeCautionFt,
        int densityAltitudeWarningFt
) {
    public static AppSettings defaults() {
        return new AppSettings(
                "",
                "C172 Trainer",
                ThemePreset.NIGHTFALL,
                TemperatureUnit.FAHRENHEIT,
                DistanceUnit.NAUTICAL_MILES,
                1.0,
                1.5,
                -5,
                3.0f,
                1000,
                5.0f,
                3000,
                3000,
                5000
        );
    }
}
