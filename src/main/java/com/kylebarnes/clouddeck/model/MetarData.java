package com.kylebarnes.clouddeck.model;

import java.util.List;
import java.util.stream.Collectors;

public record MetarData(
        String airportId,
        String airportName,
        String rawObservation,
        String observationTime,
        int windDir,
        int windSpeed,
        int windGust,
        float altimeterInHg,
        String flightCategory,
        List<CloudLayer> cloudLayers,
        float tempC,
        float visibilitySm
) {

    public String cloudLayersSummary() {
        if (cloudLayers == null || cloudLayers.isEmpty()) {
            return "Clear";
        }

        return cloudLayers.stream()
                .map(layer -> layer.cover() + " at " + layer.baseFt() + "ft")
                .collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        String windString = windGust > 0
                ? String.format("%03d at %d kts gusting %d kts", windDir, windSpeed, windGust)
                : String.format("%03d at %d kts", windDir, windSpeed);

        return String.format(
                "Airport: %s | Time: %s | Wind: %s | Altimeter: %.2f | Category: %s | Clouds: %s",
                airportId, observationTime, windString, altimeterInHg, flightCategory, cloudLayersSummary()
        );
    }
}
