package com.kylebarnes.clouddeck.model;

public record RoutePlan(
        AirportInfo departureAirport,
        AirportInfo destinationAirport,
        double distanceNm,
        double groundspeedKts,
        double estimatedTimeHours,
        double airborneFuelGallons,
        double taxiFuelGallons,
        double climbFuelGallons,
        double tripFuelGallons,
        double reserveRemainingGallons,
        boolean reserveSatisfied
) {
}
