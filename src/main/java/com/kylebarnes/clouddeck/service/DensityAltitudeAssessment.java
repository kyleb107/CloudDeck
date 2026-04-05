package com.kylebarnes.clouddeck.service;

public record DensityAltitudeAssessment(
        int pressureAltitudeFt,
        int densityAltitudeFt,
        VfrStatusLevel level,
        String message
) {
}
