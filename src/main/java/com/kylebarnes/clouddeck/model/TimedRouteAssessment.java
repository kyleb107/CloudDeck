package com.kylebarnes.clouddeck.model;

import com.kylebarnes.clouddeck.service.RouteDecisionLevel;
import com.kylebarnes.clouddeck.service.VfrAssessment;

import java.time.LocalDateTime;

public record TimedRouteAssessment(
        RouteDecisionLevel level,
        String message,
        LocalDateTime departureTimeUtc,
        LocalDateTime arrivalTimeUtc,
        VfrAssessment departureForecastAssessment,
        VfrAssessment destinationForecastAssessment
) {
}
