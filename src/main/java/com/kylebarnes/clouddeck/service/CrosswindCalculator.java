package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.CrosswindComponents;

public final class CrosswindCalculator {

    private CrosswindCalculator() {
    }

    public static CrosswindComponents calculate(int runwayHeading, int windDir, int windSpeed) {
        double angleDiff = Math.toRadians(windDir - runwayHeading);
        double headwind = windSpeed * Math.cos(angleDiff);
        double crosswind = windSpeed * Math.sin(angleDiff);
        return new CrosswindComponents(headwind, crosswind);
    }

    public static String formatTerminalOutput(int runwayHeading, int windDir, int windSpeed) {
        CrosswindComponents components = calculate(runwayHeading, windDir, windSpeed);
        String headwindLabel = components.headwindKts() >= 0 ? "Headwind" : "Tailwind";
        String crosswindLabel = components.crosswindKts() >= 0 ? "From the right" : "From the left";

        return String.format(
                "Runway %02d | %s: %.1f kts | Crosswind: %.1f kts (%s)",
                runwayHeading / 10,
                headwindLabel,
                Math.abs(components.headwindKts()),
                Math.abs(components.crosswindKts()),
                crosswindLabel
        );
    }
}
