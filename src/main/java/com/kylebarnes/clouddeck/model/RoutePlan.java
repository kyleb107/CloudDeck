package com.kylebarnes.clouddeck.model;

public record RoutePlan(
        AirportInfo departureAirport,
        AirportInfo destinationAirport,
        double distanceNm,
        double estimatedTimeHours,
        double tripFuelGallons,
        double reserveRemainingGallons,
        boolean reserveSatisfied
) {
}
