package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.CloudLayer;
import com.kylebarnes.clouddeck.model.MetarData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MetarTrendService {
    private final FlightConditionEvaluator flightConditionEvaluator = new FlightConditionEvaluator();

    public MetarTrendSummary summarize(List<MetarData> history, AppSettings settings) {
        if (history == null || history.size() < 2) {
            return null;
        }

        List<MetarData> sorted = new ArrayList<>(history);
        sorted.sort(Comparator.comparing(MetarData::observationTime));

        MetarData earliest = sorted.getFirst();
        MetarData latest = sorted.getLast();
        int earliestSeverity = categorySeverity(earliest.flightCategory());
        int latestSeverity = categorySeverity(latest.flightCategory());
        float visibilityDelta = latest.visibilitySm() - earliest.visibilitySm();
        int earliestCeiling = ceilingFeet(earliest.cloudLayers());
        int latestCeiling = ceilingFeet(latest.cloudLayers());
        int ceilingDelta = latestCeiling - earliestCeiling;

        String headline;
        if ((latestSeverity - earliestSeverity) >= 1 || visibilityDelta <= -2.0f || ceilingDelta <= -1000) {
            headline = "Deteriorating over last 12 hours";
        } else if ((latestSeverity - earliestSeverity) <= -1 || visibilityDelta >= 2.0f || ceilingDelta >= 1000) {
            headline = "Improving over last 12 hours";
        } else {
            headline = "Mostly steady over last 12 hours";
        }

        List<MetarData> recentObservations = sorted.stream()
                .sorted(Comparator.comparing(MetarData::observationTime).reversed())
                .limit(4)
                .toList();

        return new MetarTrendSummary(
                flightConditionEvaluator.assessVfr(latest, settings).level(),
                headline,
                "Flight category: " + earliest.flightCategory() + " -> " + latest.flightCategory(),
                "Visibility: " + formatOneDecimal(earliest.visibilitySm()) + " -> " + formatOneDecimal(latest.visibilitySm()) + " SM",
                "Ceiling: " + formatCeiling(earliestCeiling) + " -> " + formatCeiling(latestCeiling),
                recentObservations
        );
    }

    private int categorySeverity(String category) {
        return switch (category) {
            case "VFR" -> 0;
            case "MVFR" -> 1;
            case "IFR" -> 2;
            case "LIFR" -> 3;
            default -> 1;
        };
    }

    private int ceilingFeet(List<CloudLayer> cloudLayers) {
        if (cloudLayers == null || cloudLayers.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return cloudLayers.stream()
                .filter(layer -> "BKN".equals(layer.cover()) || "OVC".equals(layer.cover()))
                .mapToInt(CloudLayer::baseFt)
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    private String formatCeiling(int ceilingFeet) {
        return ceilingFeet == Integer.MAX_VALUE ? "No ceiling" : ceilingFeet + " ft";
    }

    private String formatOneDecimal(float value) {
        return String.format("%.1f", value);
    }
}
