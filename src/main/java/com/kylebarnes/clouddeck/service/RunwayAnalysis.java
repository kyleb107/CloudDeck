package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.CrosswindComponents;
import com.kylebarnes.clouddeck.model.Runway;

public record RunwayAnalysis(
        Runway runway,
        CrosswindComponents components,
        boolean bestOption,
        boolean exceedsAircraftLimit
) {
}
