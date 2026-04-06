package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.MetarData;

import java.util.List;

public record MetarTrendSummary(
        VfrStatusLevel level,
        String headline,
        String categorySummary,
        String visibilitySummary,
        String ceilingSummary,
        List<MetarData> recentObservations
) {
}
