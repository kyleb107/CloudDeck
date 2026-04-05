package com.kylebarnes.clouddeck.model;

import com.kylebarnes.clouddeck.service.VfrAssessment;

public record AlternateAirportOption(
        AirportWeather airportWeather,
        double distanceFromDestinationNm,
        VfrAssessment vfrAssessment,
        boolean hasUsableRunway,
        String summary
) {
}
