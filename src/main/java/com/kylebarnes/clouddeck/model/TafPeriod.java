package com.kylebarnes.clouddeck.model;

import java.util.List;

public record TafPeriod(
        String label,
        String type,
        Integer windDir,
        int windSpeed,
        int windGust,
        Float visibilitySm,
        List<CloudLayer> cloudLayers,
        List<String> weatherTokens,
        String rawText
) {

    public String cloudLayersSummary() {
        if (cloudLayers == null || cloudLayers.isEmpty()) {
            return "No ceiling listed";
        }

        return cloudLayers.stream()
                .map(layer -> layer.cover() + " at " + layer.baseFt() + "ft")
                .reduce((left, right) -> left + ", " + right)
                .orElse("No ceiling listed");
    }
}
