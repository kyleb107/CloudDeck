package com.kylebarnes.clouddeck.model;

public record AppSettings(
        String homeAirport,
        String defaultAircraftName,
        TemperatureUnit temperatureUnit,
        DistanceUnit distanceUnit,
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
                TemperatureUnit.FAHRENHEIT,
                DistanceUnit.NAUTICAL_MILES,
                3.0f,
                1000,
                5.0f,
                3000,
                3000,
                5000
        );
    }
}
